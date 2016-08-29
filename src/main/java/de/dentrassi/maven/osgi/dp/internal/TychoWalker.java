/*******************************************************************************
 * Copyright (c) 2016 Jens Reimann.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann <jreimann@redhat.com> - initial API and implementation
 *******************************************************************************/
package de.dentrassi.maven.osgi.dp.internal;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.ArtifactDependencyVisitor;
import org.eclipse.tycho.core.ArtifactDependencyWalker;
import org.eclipse.tycho.core.FeatureDescription;
import org.eclipse.tycho.core.PluginDescription;

public class TychoWalker implements ArtifactWalker {

    private final ArtifactDependencyWalker walker;
    private final Log logger;

    public TychoWalker(final ArtifactDependencyWalker walker, final Log logger) {
        this.walker = walker;
        this.logger = logger;
    }

    @Override
    public void walk(final ArtifactConsumer consumer) {
        this.walker.walk(new ArtifactDependencyVisitor() {
            @Override
            public boolean visitFeature(final FeatureDescription feature) {
                return true;
            }

            @Override
            public void visitPlugin(final PluginDescription plugin) {
                try {
                    TychoWalker.this.logger.debug(format("Considering artifact: %s", plugin));

                    if (!"eclipse-plugin".equalsIgnoreCase(plugin.getKey().getType())) {
                        TychoWalker.this.logger.debug(format("Not a JAR file -> %s", plugin.getKey().getType()));
                        return;
                    }

                    final File location;
                    final ReactorProject p = plugin.getMavenProject();
                    if (p != null) {
                        location = p.getArtifact();
                    } else {
                        location = plugin.getLocation();
                    }

                    if (location == null) {
                        TychoWalker.this.logger.warn(format("Unable to locate artifact: %s", plugin));
                    }

                    if (!location.isFile()) {
                        TychoWalker.this.logger.warn(format("Location '%s' is not a file", location));
                        return;
                    }

                    consumer.accept(new Entry(location.toPath()));
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

}
