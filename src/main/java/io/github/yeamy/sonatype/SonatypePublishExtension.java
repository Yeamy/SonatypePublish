package io.github.yeamy.sonatype;


import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public class SonatypePublishExtension {

    private final Property<Integer> port;
    private final Property<Boolean> push;
    private final Property<Boolean> autoPublish;

    @Inject
    public SonatypePublishExtension(ObjectFactory objects) {
        port = objects.property(Integer.class);
        port.convention(8081);
        push = objects.property(Boolean.class);
        push.convention(false);
        autoPublish = objects.property(Boolean.class);
        autoPublish.convention(false);
    }

    public Property<Integer> getPort() {
        return port;
    }

    public Property<Boolean> getPush() {
        return push;
    }

    public Property<Boolean> getAutoPublish() {
        return autoPublish;
    }
}
