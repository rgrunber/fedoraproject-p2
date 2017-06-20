/*******************************************************************************
 * Copyright (c) 2014-2017 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2.xmvn;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fedoraproject.p2.installer.Dropin;
import org.fedoraproject.p2.installer.EclipseArtifact;
import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstallationResult;
import org.fedoraproject.p2.installer.EclipseInstaller;
import org.fedoraproject.p2.installer.EclipseInstallerFactory;
import org.fedoraproject.xmvn.artifact.Artifact;
import org.fedoraproject.xmvn.artifact.DefaultArtifact;
import org.fedoraproject.xmvn.config.PackagingRule;
import org.fedoraproject.xmvn.metadata.ArtifactMetadata;
import org.fedoraproject.xmvn.tools.install.ArtifactInstallationException;
import org.fedoraproject.xmvn.tools.install.ArtifactInstaller;
import org.fedoraproject.xmvn.tools.install.Directory;
import org.fedoraproject.xmvn.tools.install.File;
import org.fedoraproject.xmvn.tools.install.JarUtils;
import org.fedoraproject.xmvn.tools.install.JavaPackage;
import org.fedoraproject.xmvn.tools.install.RegularFile;
import org.fedoraproject.xmvn.tools.install.SymbolicLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EclipseArtifactInstaller implements ArtifactInstaller {
	private final Logger logger = LoggerFactory
			.getLogger(EclipseArtifactInstaller.class);

	private final EclipseInstallationRequest request = new EclipseInstallationRequest();

	private final Map<String, JavaPackage> packageMap = new LinkedHashMap<>();

	@Override
	public void install(JavaPackage targetPackage, ArtifactMetadata am,
			PackagingRule rule, String basePackageName) {
		Path path = Paths.get(am.getPath());

		if (!am.getExtension().equals("jar")
				|| (!am.getClassifier().isEmpty() && !am.getClassifier().equals("sources")
						&& !am.getClassifier().equals("sources-feature")))
			return;

		boolean isNative = false;
		if (JarUtils.usesNativeCode(path) || JarUtils.containsNativeCode(path)) {
			isNative = true;
		}

		String type = am.getProperties().getProperty("type");
		boolean isFeature = type.equals("eclipse-feature");
		EclipseArtifact provide;
		if (type.equals("eclipse-plugin") || type.equals("eclipse-test-plugin"))
			provide = new XMvnEclipseArtifact(path, false, isNative, am);
		else if (isFeature)
			provide = new XMvnEclipseArtifact(path, true, isNative, am);
		else
			return;
		request.addArtifact(provide);

		Artifact artifact = new DefaultArtifact(am.getGroupId(),
				am.getArtifactId(), am.getExtension(), am.getClassifier(),
				am.getVersion());
		logger.info("Installing artifact {}", artifact);

		String commonId = basePackageName.replaceAll("^eclipse-", "");
		request.setMainPackageId(commonId);
		String subpackageId = targetPackage.getId().replaceAll("^eclipse-", "");
		if (subpackageId.isEmpty())
			subpackageId = commonId;
		else if (!subpackageId.startsWith(commonId + "-"))
			subpackageId = commonId + "-" + subpackageId;

		if (isFeature
				|| (rule.getTargetPackage() != null && !rule.getTargetPackage()
						.isEmpty())) {
			provide.setTargetPackage(subpackageId);
		}

		packageMap.put(subpackageId, targetPackage);
	}

	@Override
	public void postInstallation() throws ArtifactInstallationException {
		try {
			Path tempRoot = Files.createTempDirectory("xmvn-root-");
			request.setBuildRoot(tempRoot);

			EclipseInstaller installer = new EclipseInstallerFactory()
					.createEmbeddedInstaller();
			EclipseInstallationResult result = installer
					.performInstallation(request);

			for (Dropin dropin : result.getDropins()) {
				JavaPackage pkg = packageMap.get(dropin.getId());
				addAllFiles(pkg, tempRoot.resolve(dropin.getPath()), tempRoot);

				for (EclipseArtifact provide : dropin.getOsgiProvides()) {
					ArtifactMetadata am = ((XMvnEclipseArtifact)provide).getMetadata();

					am.setPath(provide.getInstalledPath().toString());
					am.getProperties().putAll(provide.getProperties());
					am.getProperties().setProperty("osgi.id", provide.getId());
					am.getProperties().setProperty("osgi.version",
							provide.getVersion());
					if (am.getProperties().getProperty("osgi.namespace") != null) {
						am.setNamespace(am.getProperties().getProperty("osgi.namespace"));
					}
					pkg.getMetadata().addArtifact(am);
				}
			}
		} catch (Exception e) {
			throw new ArtifactInstallationException(
					"Unable to install Eclipse artifacts", e);
		}
	}

	private void addAllFiles(JavaPackage pkg, Path dropin, Path root)
			throws IOException {
		pkg.addFile(new Directory(root.relativize(dropin)));

		if (Files.isDirectory(dropin)) {
			// Generate list of paths before recursing to avoid running
			// out of file handles
			List<Path> paths = new ArrayList<>();
			try(DirectoryStream<Path> stream = Files.newDirectoryStream(dropin)) {
				for (Path path : stream) {
					paths.add(path);
				}
			}
			for (Path path : paths) {
				Path relativePath = root.relativize(path);
				if (Files.isDirectory(path))
					addAllFiles(pkg, path, root);
				else {
					File f;

					if (Files.isSymbolicLink(path))
						f = new SymbolicLink(relativePath,
								Files.readSymbolicLink(path));
					else
						f = new RegularFile(relativePath, path);
					pkg.addFile(f);
				}
			}
		}
	}
}
