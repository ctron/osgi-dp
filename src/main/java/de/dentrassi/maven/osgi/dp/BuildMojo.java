/*******************************************************************************
 * Copyright (c) 2016 Jens Reimann
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann <jreimann@redhat.com> - initial API and implementation
 *******************************************************************************/
package de.dentrassi.maven.osgi.dp;

import java.nio.file.Path;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Build an OSGi distribution package based on the existing project
 * <p>
 * This mojo allows building an OSGi DP based on the main artifact, optional
 * Tycho feature dependencies and additional dependencies.
 * </p>
 *
 * @author Jens Reimann
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME, threadSafe = false)
public class BuildMojo extends AbstractDpMojo {

    /**
     * Whether or not the resulting DP should be attached to the project output
     */
    @Parameter(defaultValue = "true")
    protected boolean attach = true;

    @Override
    protected void attach(final Path out) {
        if (this.attach) {
            this.projectHelper.attachArtifact(this.project, "dp", out.toFile());
        }
    }
}
