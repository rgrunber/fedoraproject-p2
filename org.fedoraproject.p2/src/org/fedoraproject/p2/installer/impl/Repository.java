/*******************************************************************************
 * Copyright (c) 2014-2015 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2.installer.impl;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;

import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.internal.repository.tools.RepositoryDescriptor;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.publisher.Publisher;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.fedoraproject.p2.P2Utils;

/**
 * @author Mikolaj Izdebski
 */
public class Repository {

	private static Path tempDir;

	private final Path location;

	private final IArtifactRepository artifactRepository;

	private final IMetadataRepository metadataRepository;

	private Repository(Path location, IArtifactRepository artifactRepository,
			IMetadataRepository metadataRepository) {
		this.location = location;
		this.artifactRepository = artifactRepository;
		this.metadataRepository = metadataRepository;
	}

	public static Repository createTemp() throws ProvisionException,
			IOException {
		Path tempDirectory = createTempDirectory();
		return create(tempDirectory);
	}

	public static Repository create(Path location) throws ProvisionException {
		IProvisioningAgent agent = P2Utils.getAgent();
		URI uri = location.toUri();
		String name = "xmvn-p2-repo";

		IArtifactRepository artifactRepository = Publisher
				.createArtifactRepository(agent, uri, name, true, true);

		IMetadataRepository metadataRepository = Publisher
				.createMetadataRepository(agent, uri, name, true, true);

		return new Repository(location, artifactRepository, metadataRepository);
	}

	public static Repository load(Path location) throws ProvisionException {
		IProvisioningAgent agent = P2Utils.getAgent();
		URI uri = location.toUri();

		IArtifactRepository artifactRepository = Publisher
				.loadArtifactRepository(agent, uri, false, false);

		IMetadataRepository metadataRepository = Publisher
				.loadMetadataRepository(agent, uri, false, false);

		return new Repository(location, artifactRepository, metadataRepository);
	}

	public Path getLocation() {
		return location;
	}

	public IArtifactRepository getArtifactRepository() {
		return artifactRepository;
	}

	public IMetadataRepository getMetadataRepository() {
		return metadataRepository;
	}

	public RepositoryDescriptor getDescripror() {
		RepositoryDescriptor descriptor = new RepositoryDescriptor();
		descriptor.setLocation(location.toUri());
		return descriptor;
	}

	private Set<IInstallableUnit> executeQuery(IQuery<IInstallableUnit> query) {
		IQueryable<IInstallableUnit> queryable = getMetadataRepository();
		IQueryResult<IInstallableUnit> result = queryable.query(query, null);
		return result.toUnmodifiableSet();
	}

	public Set<IInstallableUnit> getAllUnits() {
		IQuery<IInstallableUnit> query = QueryUtil.createIUAnyQuery();
		return executeQuery(query);
	}

	private static synchronized Path createTempDirectory() throws IOException {
		if (tempDir == null) {
			tempDir = Files.createTempDirectory("xmvn-p2-");

			Runtime.getRuntime().addShutdownHook(
					new Thread(new TempDirRemover()));
		}

		return Files.createTempDirectory(tempDir, "");
	}

	private static class TempDirRemover implements Runnable {
		@Override
		public void run() {
			try {
				delete(tempDir);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void delete(Path path) throws IOException {
			if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
				for (Path child : Files.newDirectoryStream(path))
					delete(child);

			Files.delete(path);
		}
	}
}
