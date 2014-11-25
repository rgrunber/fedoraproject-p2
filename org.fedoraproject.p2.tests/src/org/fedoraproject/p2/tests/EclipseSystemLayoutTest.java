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
package org.fedoraproject.p2.tests;

import static org.fedoraproject.p2.EclipseSystemLayout.getSCLRoots;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;

import org.junit.Test;

public class EclipseSystemLayoutTest extends RepositoryTest {

	@Test
	public void nullSclRootsTest() throws Exception {
		Set<String> roots = getSCLRoots(null);
		assertEquals(1, roots.size());
		assertEquals("/", roots.iterator().next());
	}

	@Test
	public void emptySclRootsTest() throws Exception {
		Set<String> roots = getSCLRoots("");
		assertEquals(1, roots.size());
		assertEquals("/", roots.iterator().next());
	}

	@Test
	public void nonRealRootTest() throws Exception {
		try {
			getSCLRoots("/usr/../etc/java//.////../");
			fail("Expected exception");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("not absolute real path"));
		}
	}

	@Test(expected = IOException.class)
	public void nonExistentRoot() throws Exception {
		getSCLRoots(getTempDir().resolve("foo").toString());
	}

	@Test
	public void nonDirectoryRoot() throws Exception {
		try {
			Path root = getTempDir().resolve("foo");
			Files.createFile(root);
			getSCLRoots(root.toString());
			fail("Expected exception");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("not represent a directory"));
		}
	}

	private String createConfDirs(String... roots) throws Exception {
		String confDirs = null;
		for (String root : roots) {
			Path dir = getTempDir().resolve(root).resolve("etc/java");
			Files.createDirectories(dir);
			String s = dir.toString();
			if (confDirs == null)
				confDirs = s;
			else
				confDirs += ":" + s;
		}
		return confDirs;
	}

	@Test
	public void orderedRootTest() throws Exception {
		String confDirs = createConfDirs("b", "d", "c", "e", "a", "XYZ");
		Set<String> roots = getSCLRoots(confDirs);
		assertEquals(6, roots.size());
		Iterator<String> it = roots.iterator();
		assertTrue(it.next().endsWith("/b"));
		assertTrue(it.next().endsWith("/d"));
		assertTrue(it.next().endsWith("/c"));
		assertTrue(it.next().endsWith("/e"));
		assertTrue(it.next().endsWith("/a"));
		assertTrue(it.next().endsWith("/XYZ"));
	}

	@Test
	public void duplicateRootTest() throws Exception {
		String confDirs = createConfDirs("foo", "bar", "foo", "baz", "bar", "XYZ");
		Set<String> roots = getSCLRoots(confDirs);
		assertEquals(4, roots.size());
		Iterator<String> it = roots.iterator();
		assertTrue(it.next().endsWith("/foo"));
		assertTrue(it.next().endsWith("/bar"));
		assertTrue(it.next().endsWith("/baz"));
		assertTrue(it.next().endsWith("/XYZ"));
	}
}
