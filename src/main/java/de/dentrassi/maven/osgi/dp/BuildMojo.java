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
package de.dentrassi.maven.osgi.dp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.DependencyResolver;

import com.google.common.io.ByteStreams;

/**
 * Build an OSGi distribution package from an Eclipse Feature
 * 
 * @author Jens Reimann
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE, threadSafe = false)
public class BuildMojo extends AbstractMojo {

	/**
	 * The maven project
	 */
	@Parameter(property = "project", readonly = true, required = true)
	protected MavenProject project;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	@Parameter(defaultValue = "true")
	protected boolean useQualifiedFilename = true;

	@Parameter(defaultValue = "true")
	protected boolean attach = true;

	@Parameter(defaultValue = "false", property = "osgi-dp.skip")
	protected boolean skip = false;

	@Component
	private MavenProjectHelper projectHelper;

	@Component
	private DependencyResolver dependencyResolver;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (this.skip)
			return;

		final String dpVersion = makeVersion(true);

		getLog().info("Building DP - Version: " + dpVersion);

		final Manifest dpmf = new Manifest();
		dpmf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1");
		dpmf.getMainAttributes().putValue("DeploymentPackage-SymbolicName", this.project.getArtifactId());
		dpmf.getMainAttributes().putValue("DeploymentPackage-Version", dpVersion);

		try {
			final Map<String, File> files = new HashMap<>();

			for (Artifact art : this.project.getArtifacts()) {
				try (JarFile jar = new JarFile(art.getFile())) {
					final Manifest mf = jar.getManifest();

					String bsn = mf.getMainAttributes().getValue("Bundle-SymbolicName");
					if (bsn == null)
						continue;

					final String version = mf.getMainAttributes().getValue("Bundle-Version");
					if (bsn != null) {
						bsn = bsn.split(";", 2)[0];
					}

					String fn = String.format("%s_%s.jar", bsn, version);

					Attributes attrs = new Attributes();
					attrs.putValue("Bundle-SymbolicName", bsn);
					attrs.putValue("Bundle-Version", version);
					dpmf.getEntries().put(fn, attrs);

					files.put(fn, art.getFile());
				}
			}

			final String dpName = String.format("%s_%s.dp", this.project.getArtifactId(),
					makeVersion(this.useQualifiedFilename));
			final Path out = Paths.get(this.project.getBuild().getDirectory(), dpName);

			getLog().info("Writing to: " + out);

			Files.createDirectories(out.getParent());

			try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(out), dpmf)) {

				for (Map.Entry<String, File> entry : files.entrySet()) {

					final Path p = entry.getValue().toPath();
					final JarEntry je = new JarEntry(entry.getKey());

					je.setSize(Files.size(p));
					je.setLastModifiedTime(Files.getLastModifiedTime(p));

					jar.putNextEntry(je);
					try (InputStream in = Files.newInputStream(p)) {
						ByteStreams.copy(in, jar);
					}
				}
			}

			if (this.attach) {
				this.projectHelper.attachArtifact(this.project, "dp", out.toFile());
			}

		} catch (IOException e) {
			throw new MojoFailureException("Failed to process", e);
		}
	}

	private String makeVersion(boolean qualified) {
		if (qualified) {
			final ReactorProject rp = (ReactorProject) this.project.getContextValue(ReactorProject.CTX_REACTOR_PROJECT);
			if (rp == null) {
				throw new IllegalStateException(
						"Failed to get qualified project version. On non-tycho projects set 'useQualifiedFilename' to 'false'");
			}
			return rp.getExpandedVersion();
		} else {
			return this.project.getVersion();
		}
	}
}
