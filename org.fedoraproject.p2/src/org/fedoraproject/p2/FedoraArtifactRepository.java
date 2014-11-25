/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.repository.IRunnableWithProgress;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRequest;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactDescriptor;

public class FedoraArtifactRepository implements IArtifactRepository {

	private IProvisioningAgent agent;
	private URI location;
	private FedoraBundleIndex index;

	public FedoraArtifactRepository (IProvisioningAgent agent, URI location) {
		this.agent = agent;
		this.location = location;
		this.index = new FedoraBundleIndex(new File(location.getPath()));
	}

	@Override
	public URI getLocation() {
		return location;
	}

	@Override
	public String getName() {
		return "Fedora Artifact Repository " + location;
	}

	@Override
	public String getType() {
		return this.getClass().getName();
	}

	@Override
	public String getVersion() {
		return "0.0.1";
	}

	@Override
	public String getDescription() {
		return "Fedora p2 Artifact Repository";
	}

	@Override
	public String getProvider() {
		return "Fedora";
	}

	@Override
	public Map<String, String> getProperties() {
		return new LinkedHashMap<> ();
	}

	@Override
	public String getProperty(String key) {
		return null;
	}

	@Override
	public IProvisioningAgent getProvisioningAgent() {
		return agent;
	}

	@Override
	//TODO: We could support this but let's be immutable for now.
	/**
	 * Mirroring artifacts from one Fedora Repository to one
	 * empty Fedora Repository is essentially a recursive
	 * directory copy!
	 */
	public boolean isModifiable() {
		return false;
	}

	@Override
	public String setProperty(String key, String value) {
		return null;
	}

	@Override
	public String setProperty(String key, String value, IProgressMonitor monitor) {
		return null;
	}

	@Override
	public Object getAdapter(Class adapter) {
		return null;
	}

	@Override
	public IQueryResult<IArtifactKey> query(IQuery<IArtifactKey> query,
			IProgressMonitor monitor) {
		return query.perform(index.getAllArtifactKeys().iterator());
	}

	@Override
	public IArtifactDescriptor createArtifactDescriptor(IArtifactKey key) {
		return new ArtifactDescriptor(key);
	}

	@Override
	public IArtifactKey createArtifactKey(String classifier, String id,
			Version version) {
		if (classifier.equals("osgi.bundle")) {
			return BundlesAction.createBundleArtifactKey(id, version.toString());
		} else if (classifier.equals("org.eclipse.update.feature")) {
			return FeaturesAction.createFeatureArtifactKey(id, version.toString());
		}
		return null;
	}

	@Override
	public void addDescriptor(IArtifactDescriptor descriptor) {
	}

	@Override
	public void addDescriptor(IArtifactDescriptor descriptor,
			IProgressMonitor monitor) {
	}

	@Override
	public void addDescriptors(IArtifactDescriptor[] descriptors) {
	}

	@Override
	public void addDescriptors(IArtifactDescriptor[] descriptors,
			IProgressMonitor monitor) {
	}

	@Override
	public boolean contains(IArtifactDescriptor descriptor) {
		return contains(descriptor.getArtifactKey());
	}

	@Override
	public boolean contains(IArtifactKey key) {
		return index.containsKey(key);
	}

	@Override
	public IStatus getArtifact(IArtifactDescriptor descriptor,
			OutputStream destination, IProgressMonitor monitor) {
		IArtifactKey key = descriptor.getArtifactKey();
		File file = index.getFileForKey(key);
		if (file == null) {
			return Status.CANCEL_STATUS;
		}
		if (key.getClassifier().equals("osgi.bundle")) {
			if (file.isDirectory()) {
				createJarFromDir(file, destination);
			} else {
				FileInputStream fi = null;
				try {
					fi = new FileInputStream(file);
					byte [] buf = new byte[4096];
					int len;
					while ((len = fi.read(buf)) != -1) {
						destination.write(buf, 0, len);
					}
				} catch (IOException e) {
				} finally {
					try {
						fi.close();
					} catch (IOException e) {
					}
				}
			}
		} else if (key.getClassifier().equals("org.eclipse.update.feature")) {
			createJarFromDir(file, destination);
		}
		return Status.OK_STATUS;
	}

	private File[] getAllFiles(File root) {
		List<File> res = new ArrayList<File>();
		for (File child : root.listFiles()) {
			File [] tmp;
			if (child.isDirectory() && child.canRead()) {
				tmp = getAllFiles(child);
			} else {
				tmp = new File [] {child};
			}
			res.addAll(Arrays.asList(tmp));
		}
		return res.toArray(new File[0]);
	}

	private void createJarFromDir (File file, OutputStream destination) {
		byte [] buf = new byte[4096];
		try (JarOutputStream out = new JarOutputStream(destination)) {
			File [] inputFiles = getAllFiles(file);
			for (File f : inputFiles) {
				String fileEntry = f.getAbsolutePath().substring(file.getAbsolutePath().length() + 1);
				JarEntry entry = new JarEntry(fileEntry);
				entry.setTime(f.lastModified());
				out.putNextEntry(entry);

				try (FileInputStream inFile = new FileInputStream(f)) {
					int nRead = inFile.read(buf);
					while (nRead > 0) {
						out.write(buf, 0, nRead);
						nRead = inFile.read(buf);
					}
				} catch (IOException e) {
				}
			}
		} catch (IOException e) {
		}
	}

	@Override
	public IStatus getRawArtifact(IArtifactDescriptor descriptor,
			OutputStream destination, IProgressMonitor monitor) {
		return getArtifact(descriptor, destination, monitor);
	}

	@Override
	public IArtifactDescriptor[] getArtifactDescriptors(IArtifactKey key) {
		if (contains(key)) {
			return new IArtifactDescriptor[] { createArtifactDescriptor(key) };
		} else {
			return new IArtifactDescriptor [0];
		}
	}

	@Override
	public IStatus getArtifacts(IArtifactRequest[] requests,
			IProgressMonitor monitor) {
		for (IArtifactRequest request : requests) {
			request.perform(this, new NullProgressMonitor());
		}
		return Status.OK_STATUS;
	}

	@Override
	public OutputStream getOutputStream(IArtifactDescriptor descriptor)
			throws ProvisionException {
		return null;
	}

	@Override
	public IQueryable<IArtifactDescriptor> descriptorQueryable() {
		return null;
	}

	@Override
	public void removeAll() {
	}

	@Override
	public void removeAll(IProgressMonitor monitor) {
	}

	@Override
	public void removeDescriptor(IArtifactDescriptor descriptor) {
	}

	@Override
	public void removeDescriptor(IArtifactDescriptor descriptor,
			IProgressMonitor monitor) {
	}

	@Override
	public void removeDescriptor(IArtifactKey key) {
	}

	@Override
	public void removeDescriptor(IArtifactKey key, IProgressMonitor monitor) {
	}

	@Override
	public void removeDescriptors(IArtifactDescriptor[] descriptors) {
	}

	@Override
	public void removeDescriptors(IArtifactDescriptor[] descriptors,
			IProgressMonitor monitor) {
	}

	@Override
	public void removeDescriptors(IArtifactKey[] keys) {
	}

	@Override
	public void removeDescriptors(IArtifactKey[] keys, IProgressMonitor monitor) {
	}

	@Override
	public IStatus executeBatch(IRunnableWithProgress runnable,
			IProgressMonitor monitor) {
		return null;
	}
}
