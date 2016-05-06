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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.internal.repository.mirroring.Mirroring;
import org.eclipse.equinox.p2.internal.repository.tools.Repo2Runnable;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.Publisher;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.osgi.framework.util.Headers;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.fedoraproject.p2.P2Utils;

/**
 * @author Mikolaj Izdebski
 */
public class Director {
	public static void publish(Repository repository, Iterable<Path> bundles,
			Iterable<Path> features) throws ProvisionException {
		PublisherInfo info = new PublisherInfo();
		info.setArtifactOptions(IPublisherInfo.A_PUBLISH
				| IPublisherInfo.A_INDEX | IPublisherInfo.A_OVERWRITE);
		info.setArtifactRepository(repository.getArtifactRepository());
		info.setMetadataRepository(repository.getMetadataRepository());

		Collection<IPublisherAction> actions = new ArrayList<>();

		if (bundles != null && bundles.iterator().hasNext()) {
			Collection<File> bundleFiles = new ArrayList<>();
			for (Path bundle : bundles)
				bundleFiles.add(bundle.toFile());

			IPublisherAction action = new BundlesAction(
					bundleFiles.toArray(new File[0])) {
				@Override
				protected IInstallableUnit doCreateBundleIU(
						BundleDescription bd, IArtifactKey key,
						IPublisherInfo info) {
					IInstallableUnit iu = super.doCreateBundleIU(bd, key, info);
					iu = P2Utils.setPath(iu, new File(bd.getLocation()));
					iu = P2Utils.setManifest(iu, (Headers) bd.getUserObject());
					return iu;
				}
			};
			actions.add(action);
		}

		if (features != null && features.iterator().hasNext()) {
			Collection<File> featureFiles = new ArrayList<>();
			for (Path feature : features)
				featureFiles.add(feature.toFile());

			IPublisherAction action = new FeaturesAction(
					featureFiles.toArray(new File[0])) {
				@Override
				protected IInstallableUnit createGroupIU(Feature feature,
						List<IInstallableUnit> childIUs,
						IPublisherInfo publisherInfo) {
					return P2Utils.setPath(super.createGroupIU(feature,
							childIUs, publisherInfo),
							new File(feature.getLocation()));
				}

				@Override
				protected IInstallableUnit generateFeatureJarIU(
						Feature feature, IPublisherInfo publisherInfo) {
					return P2Utils.setPath(
							super.generateFeatureJarIU(feature, publisherInfo),
							new File(feature.getLocation()));
				}
			};
			actions.add(action);
		}

		Publisher publisher = new Publisher(info);
		IStatus status = publisher.publish(
				actions.toArray(new IPublisherAction[0]), null);
		if (!status.isOK())
			throw new ProvisionException(status);
	}

	public static void repo2runnable(Repository destinationRepository,
			Repository sourceRepository) throws ProvisionException {
		Repo2Runnable repo2Runnable = new Repo2Runnable();
		repo2Runnable.addSource(sourceRepository.getDescripror());
		repo2Runnable.addDestination(destinationRepository.getDescripror());
		repo2Runnable.setFlagAsRunnable(true);
		repo2Runnable.setCreateFragments(true);
		IStatus status = repo2Runnable.run(null);
		if (!status.isOK())
			throw new ProvisionException(status);
	}

	public static void mirror(Repository destinationRepository,
			Repository sourceRepository, Set<IInstallableUnit> units)
			throws ProvisionException {
		IMetadataRepository destMr = destinationRepository
				.getMetadataRepository();
		destMr.addInstallableUnits(units);

		IArtifactRepository destAr = destinationRepository
				.getArtifactRepository();
		IArtifactRepository sourceAr = sourceRepository.getArtifactRepository();

		Collection<IArtifactKey> artifactKeys = new ArrayList<>();
		for (IInstallableUnit iInstallableUnit : units)
			artifactKeys.addAll(iInstallableUnit.getArtifacts());

		Mirroring mirror = new Mirroring(sourceAr, destAr, true);
		mirror.setArtifactKeys(artifactKeys
				.toArray(new IArtifactKey[artifactKeys.size()]));
		IStatus status = mirror.run(true, false);
		if (!status.isOK())
			throw new ProvisionException(status);
	}
}
