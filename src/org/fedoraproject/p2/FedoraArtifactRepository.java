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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.repository.IRunnableWithProgress;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRequest;
import org.eclipse.equinox.p2.repository.artifact.IFileArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactDescriptor;

public class FedoraArtifactRepository implements IArtifactRepository {

	private IProvisioningAgent agent;
	private File location;

	public FedoraArtifactRepository (IProvisioningAgent agent, File location) {
		this.agent = agent;
		this.location = location;
	}

	@Override
	public URI getLocation() {
		return location.toURI();
	}

	@Override
	public String getName() {
		return "Fedora Artifact Repository " + location.getAbsolutePath();
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
		return new HashMap<String, String> ();
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
		return null;
	}

	@Override
	public IArtifactDescriptor createArtifactDescriptor(IArtifactKey key) {
		return new ArtifactDescriptor(key);
	}

	@Override
	public IArtifactKey createArtifactKey(String classifier, String id,
			Version version) {
		return new FedoraArtifactKey(classifier, id, version);
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
		return false;
	}

	@Override
	public boolean contains(IArtifactKey key) {
		return FedoraBundleIndex.getInstance().containsKey(key);
	}

	@Override
	public IStatus getArtifact(IArtifactDescriptor descriptor,
			OutputStream destination, IProgressMonitor monitor) {
		IArtifactKey key = descriptor.getArtifactKey();
		File file = FedoraBundleIndex.getInstance().getFileForKey(key);
		FileInputStream fi = null;
		try {
			fi = new FileInputStream(file);
			byte [] buf = new byte[1024];
			while (fi.read(buf) != -1) {
				destination.write(buf);
			}
		} catch (IOException e) {
		} finally {
			try {
				fi.close();
			} catch (IOException e) {
			}
		}
		return Status.OK_STATUS;
	}

	@Override
	public IStatus getRawArtifact(IArtifactDescriptor descriptor,
			OutputStream destination, IProgressMonitor monitor) {
		return null;
	}

	@Override
	public IArtifactDescriptor[] getArtifactDescriptors(IArtifactKey key) {
		return new IArtifactDescriptor [] {createArtifactDescriptor(key)};
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
