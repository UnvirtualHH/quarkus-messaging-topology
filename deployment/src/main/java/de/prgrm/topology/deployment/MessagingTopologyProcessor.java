package de.prgrm.topology.deployment;

import java.util.Collection;
import java.util.List;

import org.jboss.jandex.*;

import de.prgrm.topology.runtime.MessagingTopologyRecorder;
import de.prgrm.topology.runtime.model.ChannelInfo;
import de.prgrm.topology.runtime.model.TopologyInfo;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class MessagingTopologyProcessor {

    private static final String FEATURE = "messaging-topology";

    private static final DotName INCOMING = DotName.createSimple("org.eclipse.microprofile.reactive.messaging.Incoming");
    private static final DotName OUTGOING = DotName.createSimple("org.eclipse.microprofile.reactive.messaging.Outgoing");
    private static final DotName CHANNEL = DotName.createSimple("org.eclipse.microprofile.reactive.messaging.Channel");

    private static final DotName EMITTER = DotName.createSimple("org.eclipse.microprofile.reactive.messaging.Emitter");
    private static final DotName MUTINY_EMITTER = DotName.createSimple("io.smallrye.reactive.messaging.MutinyEmitter");
    private static final DotName MULTI = DotName.createSimple("io.smallrye.mutiny.Multi");
    private static final DotName PUBLISHER = DotName.createSimple("org.reactivestreams.Publisher");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        de.prgrm.topology.runtime.web.TopologyViewController.class,
                        de.prgrm.topology.runtime.web.TopologyApiController.class,
                        de.prgrm.topology.runtime.service.SchemaIntrospector.class,
                        de.prgrm.topology.runtime.service.MessageSender.class,
                        de.prgrm.topology.runtime.service.ServiceRegistry.class,
                        de.prgrm.topology.runtime.generator.MermaidDiagramGenerator.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void scanAndRegisterTopology(
            MessagingTopologyRecorder recorder,
            io.quarkus.deployment.builditem.CombinedIndexBuildItem combinedIndex,
            io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem curateOutcome) {

        IndexView index = combinedIndex.getIndex();

        Collection<AnnotationInstance> incomingAnnotations = index.getAnnotations(INCOMING);
        Collection<AnnotationInstance> outgoingAnnotations = index.getAnnotations(OUTGOING);
        Collection<AnnotationInstance> channelAnnotations = index.getAnnotations(CHANNEL);

        var appModel = curateOutcome.getApplicationModel();
        var appArtifact = appModel.getAppArtifact();

        String serviceName = appArtifact.getArtifactId();
        String groupId = appArtifact.getGroupId();
        String version = appArtifact.getVersion();

        TopologyInfo topology = new TopologyInfo();
        topology.setServiceName(serviceName);
        topology.setGroupId(groupId);
        topology.setArtifactId(serviceName);
        topology.setVersion(version);

        for (AnnotationInstance annotation : incomingAnnotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method = annotation.target().asMethod();
            String channelName = annotation.value().asString();

            ChannelInfo channelInfo = new ChannelInfo(
                    channelName, "incoming",
                    method.declaringClass().name().toString(), method.name());

            if (method.parametersCount() > 0) {
                Type paramType = method.parameterType(0);
                String messageType = extractMessageTypeName(paramType);
                if (messageType != null) {
                    channelInfo.setMessageType(messageType);
                }
            }

            topology.addChannel(channelInfo);
        }

        for (AnnotationInstance annotation : outgoingAnnotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method = annotation.target().asMethod();
            String channelName = annotation.value().asString();

            ChannelInfo channelInfo = new ChannelInfo(
                    channelName, "outgoing",
                    method.declaringClass().name().toString(), method.name());

            Type returnType = method.returnType();
            String messageType = extractMessageTypeName(returnType);
            if (messageType != null) {
                channelInfo.setMessageType(messageType);
            }

            topology.addChannel(channelInfo);
        }

        for (AnnotationInstance annotation : channelAnnotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.FIELD) {
                continue;
            }

            FieldInfo field = annotation.target().asField();
            String channelName = annotation.value().asString();

            Type fieldType = field.type();
            String direction = determineDirection(fieldType);

            ChannelInfo channelInfo = new ChannelInfo(
                    channelName, direction,
                    field.declaringClass().name().toString(), field.name());

            String messageType = extractMessageTypeName(fieldType);
            if (messageType != null) {
                channelInfo.setMessageType(messageType);
            }

            topology.addChannel(channelInfo);
        }

        recorder.registerTopology(topology);
    }

    private String determineDirection(Type fieldType) {
        DotName typeName = fieldType.name();

        if (typeName.equals(EMITTER) || typeName.equals(MUTINY_EMITTER)) {
            return "outgoing";
        }

        if (typeName.equals(MULTI) || typeName.equals(PUBLISHER)) {
            return "incoming";
        }

        if (fieldType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType paramType = fieldType.asParameterizedType();
            DotName rawType = paramType.name();

            if (rawType.equals(EMITTER) || rawType.equals(MUTINY_EMITTER)) {
                return "outgoing";
            }
            if (rawType.equals(MULTI) || rawType.equals(PUBLISHER)) {
                return "incoming";
            }
        }

        return "outgoing";
    }

    private String extractMessageTypeName(Type type) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType pType = type.asParameterizedType();
            List<Type> args = pType.arguments();

            if (!args.isEmpty()) {
                Type firstArg = args.getFirst();
                if (firstArg.kind() == Type.Kind.CLASS) {
                    return firstArg.name().toString();
                }
            }
        } else if (type.kind() == Type.Kind.CLASS) {
            return type.name().toString();
        }
        return null;
    }
}
