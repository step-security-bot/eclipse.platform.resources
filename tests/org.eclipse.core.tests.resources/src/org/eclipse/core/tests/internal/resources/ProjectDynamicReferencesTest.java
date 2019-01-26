/*******************************************************************************
 * Copyright (c) 2010, 2015 Broadcom Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Broadcom Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.internal.resources;

import java.util.*;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.tests.resources.ResourceTest;

/**
 * Test project dynamic references provided by extension point
 * <code>org.eclipse.core.resources.builders<code> and dynamicReference
 * {@link IDynamicReferenceProvider}
 */
public class ProjectDynamicReferencesTest extends ResourceTest {
	private static final String PROJECT_0_NAME = "ProjectDynamicReferencesTest_p0";

	private static final IProject[] EMPTY_PROJECTS = new IProject[0];

	private IProject project0;
	private IProject project1;
	private IProject project2;

	public static Test suite() {
		return new TestSuite(ProjectDynamicReferencesTest.class);
	}

	public ProjectDynamicReferencesTest(String name) {
		super(name);
	}

	@Override
	public void setUp() throws Exception {
		project0 = getWorkspace().getRoot().getProject(PROJECT_0_NAME);
		project1 = getWorkspace().getRoot().getProject("ProjectDynamicReferencesTest_p1");
		project2 = getWorkspace().getRoot().getProject("ProjectDynamicReferencesTest_p2");
		ensureExistsInWorkspace(new IProject[] { project0, project1, project2 }, true);
		addBuilder(project0);
		addBuilder(project1);
		addBuilder(project2);
	}

	private static void addBuilder(IProject project) throws CoreException {
		IProjectDescription description = project.getDescription();
		ICommand command = description.newCommand();
		command.setBuilderName(Builder.NAME);
		description.setBuildSpec(new ICommand[] {command});
		project.setDescription(description, null);
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		DynamicReferenceProvider.clear();
		project0.delete(true, null);
		project1.delete(true, null);
		project2.delete(true, null);
	}

	public void testReferences() throws CoreException {
		assertEquals("Project0 must not have referenced projects", EMPTY_PROJECTS, project0.getReferencedProjects());
		assertEquals("Project1 must not have referenced projects", EMPTY_PROJECTS, project1.getReferencedProjects());
		assertEquals("Project2 must not have referenced projects", EMPTY_PROJECTS, project2.getReferencedProjects());

		DynamicReferenceProvider.addReference(project0, project1);

		assertEquals("Project0 must not have referenced projects", EMPTY_PROJECTS, project0.getReferencedProjects());
		assertEquals("Project1 must not have referenced projects", EMPTY_PROJECTS, project1.getReferencedProjects());
		assertEquals("Project2 must not have referenced projects", EMPTY_PROJECTS, project2.getReferencedProjects());

		clearCache();

		assertEquals("Project0 must reference Project1", new IProject[] { project1 }, project0.getReferencedProjects());
		assertEquals("Project1 must not have referenced projects", EMPTY_PROJECTS, project1.getReferencedProjects());
		assertEquals("Project2 must not have referenced projects", EMPTY_PROJECTS, project2.getReferencedProjects());

		DynamicReferenceProvider.addReference(project1, project2);

		assertEquals("Project0 must reference Project1", new IProject[] { project1 }, project0.getReferencedProjects());
		assertEquals("Project1 must not have referenced projects", EMPTY_PROJECTS, project1.getReferencedProjects());
		assertEquals("Project2 must not have referenced projects", EMPTY_PROJECTS, project2.getReferencedProjects());

		clearCache();

		assertEquals("Project0 must reference Project1", new IProject[] { project1 }, project0.getReferencedProjects());
		assertEquals("Project1 must reference Project2", new IProject[] { project2 }, project1.getReferencedProjects());
		assertEquals("Project2 must not have referenced projects", EMPTY_PROJECTS, project2.getReferencedProjects());

		DynamicReferenceProvider.addReference(project0, project2);

		assertEquals("Project0 must reference Project1", new IProject[] { project1 }, project0.getReferencedProjects());
		assertEquals("Project1 must reference Project2", new IProject[] { project2 }, project1.getReferencedProjects());
		assertEquals("Project2 must not have referenced projects", EMPTY_PROJECTS, project2.getReferencedProjects());

		clearCache();

		assertEquals("Project0 must reference Project1 and Project2", new IProject[] { project1, project2 },
				project0.getReferencedProjects());
		assertEquals("Project1 must reference Project2", new IProject[] { project2 },
				project1.getReferencedProjects());
		assertEquals("Project2 must not have referenced projects", EMPTY_PROJECTS, project2.getReferencedProjects());
	}

	// Temporarily disabled, see bug 543776 comment 7
	public void XXXtestBug543776() throws CoreException {
		IFile projectFile = project0.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		String projectDescription = readStringInFileSystem(projectFile);
		projectDescription = projectDescription.replace(PROJECT_0_NAME, "anotherName");
		ensureExistsInWorkspace(projectFile, projectDescription);
		project0.delete(false, true, null);
		project0.create(null);
		project0.open(null);

		assertEquals(PROJECT_0_NAME, project0.getName());
		assertEquals("anotherName", project0.getDescription().getName());

		DynamicReferenceProvider.addReference(project0, project1);
		clearCache();

		assertEquals("Project0 must reference Project1", new IProject[] { project1 }, project0.getReferencedProjects());
	}

	private void clearCache() {
		project0.clearCachedDynamicReferences();
		project1.clearCachedDynamicReferences();
		project2.clearCachedDynamicReferences();
	}

	public static final class Builder extends IncrementalProjectBuilder {

		public static final String NAME = "org.eclipse.core.tests.resources.dynamicProjectReferenceBuilder";

		@Override
		protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
			return null;
		}

	}

	public static final class DynamicReferenceProvider implements IDynamicReferenceProvider
	{
		private static final Map<IProject, List<IProject>> dependentProjects = new HashMap<>();

		@Override
		public List<IProject> getDependentProjects(IBuildConfiguration buildConfiguration) throws CoreException {
			IProject project = buildConfiguration.getProject();
			List<IProject> depProjects = dependentProjects.get(project);
			if (depProjects != null) {
				return depProjects;
			}
			return Collections.emptyList();
		}

		public static void addReference(IProject project, IProject dependentProject) {
			List<IProject> depProjects = dependentProjects.computeIfAbsent(project, proj -> new ArrayList<>());
			depProjects.add(dependentProject);
		}

		public static void clear() {
			dependentProjects.clear();
		}
	}
}