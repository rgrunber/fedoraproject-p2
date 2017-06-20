/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2.installer;

import org.fedoraproject.p2.osgi.impl.DefaultOSGiConfigurator;
import org.fedoraproject.p2.osgi.impl.DefaultOSGiFramework;
import org.fedoraproject.p2.osgi.impl.DefaultOSGiServiceLocator;
import org.fedoraproject.xmvn.locator.ServiceLocatorFactory;
import org.fedoraproject.xmvn.resolver.Resolver;

/**
 * @author Mikolaj Izdebski
 */
public class EclipseInstallerFactory {

	public EclipseInstaller createEmbeddedInstaller(){
		return new DefaultOSGiServiceLocator(
			new DefaultOSGiFramework(
				new DefaultOSGiConfigurator(
					new ServiceLocatorFactory()
						.createServiceLocator()
						.getService(Resolver.class)
				)
			)
		).getService(EclipseInstaller.class);
	}
}
