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

import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

public class ProjectWalker implements ArtifactWalker {

    private final MavenProject project;

    public ProjectWalker(final MavenProject project) {
        this.project = project;
    }

    @Override
    public void walk(final ArtifactConsumer consumer) {
        final Artifact art = this.project.getArtifact();

        if (art.getFile() == null) {
            return;
        }

        if (art.getFile().getName().toLowerCase().endsWith(".jar")) {
            try {
                consumer.accept(new Entry(art.getFile().toPath()));
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
