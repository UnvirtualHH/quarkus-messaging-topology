package de.prgrm.topology.deployment;

import java.util.Collection;
import java.util.List;

import org.jboss.jandex.*;

import de.prgrm.topology.runtime.MessagingTopologyRecorder;
import de.prgrm.topology.runtime.generator.MermaidDiagramGenerator;
import de.prgrm.topology.runtime.model.ChannelInfo;
import de.prgrm.topology.runtime.model.TopologyInfo;
import de.prgrm.topology.runtime.service.MessageSender;
import de.prgrm.topology.runtime.service.SchemaIntrospector;
import de.prgrm.topology.runtime.service.ServiceRegistry;
import de.prgrm.topology.runtime.web.TopologyApiController;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BuildTimeConditionBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
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

    /**
     * Beans and REST resources of the extension. They expose internal application details and allow sending
     * messages, so they must only be active in dev mode.
     */
    private static final List<Class<?>> DEV_ONLY_CLASSES = List.of(
            TopologyApiController.class,
            SchemaIntrospector.class,
            MessageSender.class,
            ServiceRegistry.class,
            MermaidDiagramGenerator.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    AdditionalBeanBuildItem registerBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(DEV_ONLY_CLASSES.toArray(Class<?>[]::new))
                .setUnremovable()
                .build();
    }

    /**
     * The runtime jar is Jandex-indexed, so its beans and JAX-RS resources would be discovered in every launch mode.
     * Exclude them explicitly outside of dev mode (same mechanism as {@code @IfBuildProfile}).
     */
    @BuildStep(onlyIfNot = IsDevelopment.class)
    void disableOutsideDevMode(CombinedIndexBuildItem combinedIndex,
            BuildProducer<BuildTimeConditionBuildItem> conditions) {
        for (Class<?> clazz : DEV_ONLY_CLASSES) {
            ClassInfo classInfo = combinedIndex.getIndex().getClassByName(DotName.createSimple(clazz));
            if (classInfo != null) {
                conditions.produce(new BuildTimeConditionBuildItem(classInfo, false));
            }
        }
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void scanAndRegisterTopology(
            MessagingTopologyRecorder recorder,
            CombinedIndexBuildItem combinedIndex,
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
            channelInfo.setInjected(true);
            channelInfo.setSendable(isEmitter(fieldType));

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

    private boolean isEmitter(Type fieldType) {
        DotName typeName = fieldType.name();
        return typeName.equals(EMITTER) || typeName.equals(MUTINY_EMITTER);
    }

    private String extractMessageTypeName(Type type) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType pType = type.asParameterizedType();
            List<Type> args = pType.arguments();

            if (!args.isEmpty()) {
                Type firstArg = args.get(0);
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
