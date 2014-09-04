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
package org.fedoraproject.p2.osgi;

import org.osgi.framework.BundleContext;

/**
 * Allows Eclipse Equinox OSGi framework to be embedded in the running JVM.
 * 
 * @author Mikolaj Izdebski
 */
public interface OSGiFramework {
	/**
	 * Obtain bundle context of embedded OSGi framework. This causes the
	 * framework to be launched if it is not running yet.
	 * 
	 * @return bundle context of embedded OSGi framework
	 */
	BundleContext getBundleContext();
}
