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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	private static BundleContext context;

	public static BundleContext getContext() {
		return context;
	}

	@Override
	public void start(BundleContext bundleContext) {
		Activator.context = bundleContext;
		/**
		 *  TODO: It would be nice if the RepositorySelectionGroup dialog
		 *  (Help -> Install New Software -> Add) supported a custom protocol (fedora ? )
		 *  instead of using 'file'. Other things may use this and we have no
		 *  guarantee they won't claim to be able to load our repository, then fail.
		 *
		 *  Switching from 'file' to 'fedora' is easy but getting the UI to recognize
		 *  our protocol would be a nice-to-have.
		 */
		//Hashtable properties = new Hashtable(1);
		//properties.put(URLConstants.URL_HANDLER_PROTOCOL, new String[] {"fedora"});
		//getContext().registerService(URLStreamHandlerService.class.getName(), new FedoraURLHandler(), properties);
	}

	@Override
	public void stop(BundleContext bundleContext) {
		Activator.context = null;
	}

}
