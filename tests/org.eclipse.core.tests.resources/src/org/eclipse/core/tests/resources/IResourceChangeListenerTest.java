/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 * IBM Corporation - Initial API and implementation
 * tammo.freese@offis.de - tests for swapping files and folders
 ******************************************************************************/
package org.eclipse.core.tests.resources;

import java.io.ByteArrayInputStream;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.tests.harness.EclipseWorkspaceTest;

public class IResourceChangeListenerTest extends EclipseWorkspaceTest {

	protected static final String VERIFIER_NAME = "TestListener";
	IFile file1; //below folder1
	IFile file2; //below folder1
	IFile file3; //below folder2
	IFolder folder1; //below project2
	IFolder folder2; //below folder1
	IFolder folder3; //same as file1

	/* some random resource handles */
	IProject project1;
	IFile project1MetaData;
	IProject project2;
	IFile project2MetaData;
	ResourceDeltaVerifier verifier;
	public static Test suite() {
		//	TestSuite suite = new TestSuite();
		//	suite.addTest(new IResourceChangeListenerTest("testMoveProject1"));
		//	return suite;

		return new TestSuite(IResourceChangeListenerTest.class);
	}
	public IResourceChangeListenerTest() {
	}
	public IResourceChangeListenerTest(String name) {
		super(name);
	}
	public void _testBenchMark_1GBYQEZ() {
		// start with a clean workspace
		getWorkspace().removeResourceChangeListener(verifier);
		try {
			getWorkspace().getRoot().delete(false, getMonitor());
		} catch (CoreException e) {
			fail("0.0", e);
		}

		// create the listener
		IResourceChangeListener listener = new IResourceChangeListener() {
			public int fCounter;
			public void resourceChanged(IResourceChangeEvent event) {
				try {
					System.out.println("Start");
					for (int i = 0; i < 10; i++) {
						fCounter = 0;
						long start = System.currentTimeMillis();
						IResourceDelta delta = event.getDelta();
						delta.accept(new IResourceDeltaVisitor() {
							public boolean visit(IResourceDelta delta) throws CoreException {
								fCounter++;
								return true;
							}
						});
						long end = System.currentTimeMillis();
						System.out.println("    Number of deltas: " + fCounter + ". Time needed: " + (end - start));
					}
					System.out.println("End");
				} catch (CoreException e) {
					fail("1.0", e);
				}
			}
		};

		// add the listener
		getWorkspace().addResourceChangeListener(listener);

		// setup the test data
		IWorkspaceRunnable body = new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				IProject project = getWorkspace().getRoot().getProject("Test");
				IProjectDescription description = getWorkspace().newProjectDescription(project.getName());
				IPath root = getWorkspace().getRoot().getLocation();
				IPath contents = root.append("temp/testing");
				description.setLocation(contents);
				project.create(description, getMonitor());
				project.open(getMonitor());
				project.refreshLocal(IResource.DEPTH_INFINITE, getMonitor());
			}
		};
		try {
			getWorkspace().run(body, getMonitor());
		} catch (CoreException e) {
			fail("2.0", e);
		}

		// touch all resources (so that they appear in the delta)
		body = new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				IResourceVisitor visitor = new IResourceVisitor() {
					public boolean visit(IResource resource) throws CoreException {
						resource.touch(getMonitor());
						return true;
					}
				};
				getWorkspace().getRoot().accept(visitor);
			}
		};
		try {
			getWorkspace().run(body, getMonitor());
		} catch (CoreException e) {
			fail("3.0", e);
		}

		// un-register our listener
		getWorkspace().removeResourceChangeListener(listener);
	}
	/**
	 * Tests that the builder is receiving an appropriate delta
	 * @see SortBuilderPlugin
	 * @see SortBuilder
	 */
	public void assertDelta() {
		verifier.waitForDelta();
		assertTrue(verifier.getMessage(), verifier.isDeltaValid());
	}

	/**
	 * Asserts that a manual traversal of the delta does not find the given resources.
	 */
	void assertNotDeltaIncludes(String message, IResourceDelta delta, IResource[] resources) {
		try {
			IResource deltaResource = delta.getResource();
			for (int i = 0; i < resources.length; i++) {
				assertTrue(message, !deltaResource.equals(resources[i]));
			}
			IResourceDelta[] children = delta.getAffectedChildren();
			for (int i = 0; i < children.length; i++) {
				assertNotDeltaIncludes(message, children[i], resources);
			}
		} catch (RuntimeException e) {
			fail(message, e);
		}
	}
	/**
	 * Asserts that a visitor traversal of the delta does not find the given resources.
	 */
	void assertNotDeltaVisits(final String message, IResourceDelta delta, final IResource[] resources) {
		try {
			delta.accept(new IResourceDeltaVisitor() {
				public boolean visit(IResourceDelta delta) throws CoreException {
					IResource deltaResource = delta.getResource();
					for (int i = 0; i < resources.length; i++) {
						assertTrue(message, !deltaResource.equals(resources[i]));
					}
					return true;
				}
			});
		} catch (CoreException e) {
			fail(message, e);
		} catch (RuntimeException e) {
			fail(message, e);
		}
	}
	/**
	 * Runs code to handle a core exception
	 */
	protected void handleCoreException(CoreException e) {
		assertTrue("CoreException: " + e.getMessage(), false);
	}
	/**
	 * Sets up the fixture, for example, open a network connection.
	 * This method is called before a test is executed.
	 */
	protected void setUp() throws Exception {
		super.setUp();

		// Create some resource handles
		project1 = getWorkspace().getRoot().getProject("Project" + 1);
		project2 = getWorkspace().getRoot().getProject("Project" + 2);
		folder1 = project1.getFolder("Folder" + 1);
		folder2 = folder1.getFolder("Folder" + 2);
		folder3 = folder1.getFolder("File" + 1);
		file1 = folder1.getFile("File" + 1);
		file2 = folder1.getFile("File" + 2);
		file3 = folder2.getFile("File" + 1);
		project1MetaData = project1.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		project2MetaData = project2.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);

		// Create and open a project, folder and file
		IWorkspaceRunnable body = new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				project1.create(getMonitor());
				project1.open(getMonitor());
				folder1.create(true, true, getMonitor());
				file1.create(getRandomContents(), true, getMonitor());
			}
		};
		verifier = new ResourceDeltaVerifier();
		getWorkspace().addResourceChangeListener(verifier, IResourceChangeEvent.POST_CHANGE);
		verifier.waitForDelta();
		try {
			getWorkspace().run(body, getMonitor());
		} catch (CoreException e) {
			fail("1.0", e);
		}
		verifier.waitForDelta();
		verifier.reset();
	}
	/**
	 * Tears down the fixture, for example, close a network connection.
	 * This method is called after a test is executed.
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		ensureDoesNotExistInWorkspace(getWorkspace().getRoot());
		getWorkspace().removeResourceChangeListener(verifier);
	}
	/*
	 * Create a resource change listener and register it for POST_AUTO_BUILD events.
	 * Ensure that you are able to modify the workspace tree.
	 */
	public void test_1GDK9OG() {
		// create the resource change listener
		IResourceChangeListener listener = new IResourceChangeListener() {
			public void resourceChanged(final IResourceChangeEvent event) {
				try {
					IWorkspaceRunnable body = new IWorkspaceRunnable() {
						public void run(IProgressMonitor monitor) throws CoreException {
								// modify the tree.
	IResourceDeltaVisitor visitor = new IResourceDeltaVisitor() {
								public boolean visit(IResourceDelta delta) throws CoreException {
									IResource resource = delta.getResource();
									try {
										resource.touch(getMonitor());
									} catch (RuntimeException e) {
										throw e;
									}
									resource.createMarker(IMarker.PROBLEM);
									return true;
								}
							};
							event.getDelta().accept(visitor);
						}
					};
					getWorkspace().run(body, getMonitor());
				} catch (CoreException e) {
					fail("1.0", e);
				}
			}
		};
		// register the listener with the workspace.
		getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.POST_AUTO_BUILD);
		try {
			IWorkspaceRunnable body = new IWorkspaceRunnable() {
					// cause a delta by touching all resources
	final IResourceVisitor visitor = new IResourceVisitor() {
					public boolean visit(IResource resource) throws CoreException {
						resource.touch(getMonitor());
						return true;
					}
				};
				public void run(IProgressMonitor monitor) throws CoreException {
					getWorkspace().getRoot().accept(visitor);
				}
			};
			getWorkspace().run(body, getMonitor());
		} catch (CoreException e) {
			fail("2.0", e);
		} finally {
			// cleanup: ensure that the listener is removed
			getWorkspace().removeResourceChangeListener(listener);
		}
	}
	public void testAddAndRemoveFile() {
		try {
			verifier.reset();
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Creating and deleting", 100);
					try {
						file2.create(getRandomContents(), true, new SubProgressMonitor(m, 50));
						file2.delete(true, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			//should not have been verified since there was no change
			assertTrue("Unexpected notification on no change", !verifier.hasBeenNotified());
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testAddAndRemoveFolder() {
		try {
			verifier.reset();
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Creating and deleting", 100);
					try {
						folder2.create(true, true, new SubProgressMonitor(m, 50));
						folder2.delete(true, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			//should not have been verified since there was no change
			assertTrue("Unexpected notification on no change", !verifier.hasBeenNotified());

		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testAddFile() {
		try {
			verifier.addExpectedChange(file2, IResourceDelta.ADDED, 0);
			file2.create(getRandomContents(), true, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testAddFileAndFolder() {
		try {
			verifier.addExpectedChange(folder2, IResourceDelta.ADDED, 0);
			verifier.addExpectedChange(file3, IResourceDelta.ADDED, 0);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Creating folder and file", 100);
					try {
						folder2.create(true, true, new SubProgressMonitor(m, 50));
						file3.create(getRandomContents(), true, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testAddFolder() {
		try {
			verifier.addExpectedChange(folder2, IResourceDelta.ADDED, 0);
			folder2.create(true, true, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testAddProject() {
		try {
			verifier.addExpectedChange(project2, IResourceDelta.ADDED, 0);
			verifier.addExpectedChange(project2MetaData, IResourceDelta.ADDED, 0);
			project2.create(getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testChangeFile() {
		try {
			/* change file1's contents */
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			file1.setContents(getRandomContents(), true, false, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testChangeFileToFolder() {
		try {
			/* change file1 into a folder */
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.CONTENT | IResourceDelta.TYPE | IResourceDelta.REPLACED);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Deleting and Creating", 100);
					try {
						file1.delete(true, new SubProgressMonitor(m, 50));
						folder3.create(true, true, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testChangeFolderToFile() {
		try {
			/* change to a folder */
			verifier.reset();
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					file1.delete(true, getMonitor());
					folder3.create(true, true, getMonitor());
				}
			}, null);
			verifier.waitForDelta();

			/* now change back to a file and verify */
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.CONTENT | IResourceDelta.TYPE | IResourceDelta.REPLACED);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Deleting and Creating", 100);
					try {
						folder3.delete(true, new SubProgressMonitor(m, 50));
						file1.create(getRandomContents(), true, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testChangeProject() {
		try {
			verifier.reset();
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					project2.create(getMonitor());
					project2.open(getMonitor());
				}
			}, null);
			verifier.waitForDelta();

			IProjectDescription desc = project2.getDescription();
			desc.setReferencedProjects(new IProject[] { project1 });
			verifier.addExpectedChange(project2, IResourceDelta.CHANGED, IResourceDelta.DESCRIPTION);
			verifier.addExpectedChange(project2MetaData, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			project2.setDescription(desc, IResource.FORCE, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testCloseOpenReplaceFile() {
		try {
			// FIXME: how to do this?
			//workspace.save(getMonitor());
			//workspace.close(getMonitor());
			//workspace.open(getMonitor());
			verifier.reset();
			getWorkspace().addResourceChangeListener(verifier);

			/* change file1's contents */
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.REPLACED | IResourceDelta.CONTENT);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Deleting and Creating", 100);
					try {
						file1.delete(true, new SubProgressMonitor(m, 50));
						file1.create(getRandomContents(), true, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testDeleteInPostBuildListener() {
		// create the resource change listener
		IResourceChangeListener listener = new IResourceChangeListener() {
			public void resourceChanged(final IResourceChangeEvent event) {
				try {
					event.getDelta().accept(new IResourceDeltaVisitor() {
						public boolean visit(IResourceDelta delta) throws CoreException {
							IResource resource = delta.getResource();
							if (resource.getType() == IResource.FILE) {
								try {
									((IFile) resource).delete(true, true, null);
								} catch (RuntimeException e) {
									throw e;
								}
							}
							return true;
						}
					});
				} catch (CoreException e) {
					fail("1.0", e);
				}
			}
		};
		// register the listener with the workspace.
		getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.POST_AUTO_BUILD);
		try {
			getWorkspace().run(new IWorkspaceRunnable() {
				// cause a delta by touching all resources
				public void run(IProgressMonitor monitor) throws CoreException {
					getWorkspace().getRoot().accept(new IResourceVisitor() {
						public boolean visit(IResource resource) throws CoreException {
							resource.touch(getMonitor());
							return true;
						}
					});
				}
			}, getMonitor());
		} catch (CoreException e) {
			fail("2.0", e);
		} finally {
			// cleanup: ensure that the listener is removed
			getWorkspace().removeResourceChangeListener(listener);
		}
	}
	/**
	 * Tests deleting a file, then moving another file to that deleted location.
	 * See bug 27527.
	 */
	public void testDeleteMoveFile() {
		try {
			verifier.reset();
			file2.create(getRandomContents(), IResource.NONE, getMonitor());
			verifier.waitForDelta();
			verifier.reset();
			int flags = IResourceDelta.REPLACED | IResourceDelta.MOVED_FROM | IResourceDelta.CONTENT;
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, flags, file2.getFullPath(), null);
			verifier.addExpectedChange(file2, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, file1.getFullPath());
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("deleting and moving", 100);
					try {
						file1.delete(IResource.NONE, new SubProgressMonitor(m, 50));
						file2.move(file1.getFullPath(), IResource.NONE, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testDeleteProject() throws CoreException {
		//test that marker deltas are fired when projects are deleted
		verifier.reset();
		final IMarker marker = project1.createMarker(IMarker.TASK);
		verifier.waitForDelta();
		class Listener1 implements IResourceChangeListener {
			public boolean done = false;
			public void resourceChanged(IResourceChangeEvent event) {
				done = true;
				IMarkerDelta[] deltas = event.findMarkerDeltas(IMarker.TASK, false);
				assertEquals("1.0", 1, deltas.length);
				assertEquals("1.1", marker.getId(), deltas[0].getId());
				assertEquals("1.2", IResourceDelta.REMOVED, deltas[0].getKind());
				synchronized (this) {
					notifyAll();
				}
			}
		};
		Listener1 listener = new Listener1();
		try {
			getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.POST_CHANGE);
			project1.delete(true, false, getMonitor());
			synchronized (listener) {
				int i = 0;
				while (!listener.done) {
					try {
						listener.wait(1000);
					} catch (InterruptedException e) {
					}
					assertTrue("2.0", ++i < 60);
				}
			}
		} finally {
			getWorkspace().removeResourceChangeListener(listener);
		}
	}
	/**
	 * Tests that team private members don't show up in resource deltas when
	 * standard traversal and visitor are used.
	 */
	public void testHiddenTeamPrivateChanges() {
		IWorkspace workspace = getWorkspace();
		final IFolder teamPrivateFolder = project1.getFolder("TeamPrivateFolder");
		final IFile teamPrivateFile = folder1.getFile("TeamPrivateFile");
		final IResource[] privateResources = new IResource[] { teamPrivateFolder, teamPrivateFile };
		IResourceChangeListener listener = new IResourceChangeListener() {
			public void resourceChanged(IResourceChangeEvent event) {
					//make sure the delta doesn't include the team private members
	assertNotDeltaIncludes("1.0", event.getDelta(), privateResources);
				//make sure a visitor does not find team private members
				assertNotDeltaVisits("1.1", event.getDelta(), privateResources);
			}
		};
		workspace.addResourceChangeListener(listener);
		try {
			//create a team private folder
			workspace.run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					teamPrivateFolder.create(true, true, getMonitor());
					teamPrivateFolder.setTeamPrivateMember(true);
				}
			}, getMonitor());
			//create children in team private folder
			IFile fileInFolder = teamPrivateFolder.getFile("FileInPrivateFolder");
			fileInFolder.create(getRandomContents(), true, getMonitor());
			//modify children in team private folder
			fileInFolder.setContents(getRandomContents(), IResource.NONE, getMonitor());
			//delete children in team private folder
			fileInFolder.delete(IResource.NONE, getMonitor());
			//delete team private folder and change some other file
			workspace.run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					teamPrivateFolder.delete(IResource.NONE, getMonitor());
					file1.setContents(getRandomContents(), IResource.NONE, getMonitor());
				}
			}, getMonitor());
			//create team private file
			workspace.run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					teamPrivateFile.create(getRandomContents(), true, getMonitor());
					teamPrivateFile.setTeamPrivateMember(true);
				}
			}, getMonitor());
			//modify team private file
			teamPrivateFile.setContents(getRandomContents(), IResource.NONE, getMonitor());
			//delete team private file
			teamPrivateFile.delete(IResource.NONE, getMonitor());
		} catch (CoreException e) {
			handleCoreException(e);
		} finally {
			workspace.removeResourceChangeListener(listener);
		}
	}
	public void testModifyMoveFile() {
		try {
			verifier.addExpectedChange(folder2, IResourceDelta.ADDED, 0);
			verifier.addExpectedChange(file1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, file3.getFullPath());
			verifier.addExpectedChange(file3, IResourceDelta.ADDED, IResourceDelta.MOVED_FROM | IResourceDelta.CONTENT, file1.getFullPath(), null);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Creating and moving", 100);
					try {
						folder2.create(true, true, new SubProgressMonitor(m, 50));
						file1.setContents(getRandomContents(), IResource.NONE, getMonitor());
						file1.move(file3.getFullPath(), true, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testMoveFile() {
		try {
			verifier.addExpectedChange(folder2, IResourceDelta.ADDED, 0);
			verifier.addExpectedChange(file1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, file3.getFullPath());
			verifier.addExpectedChange(file3, IResourceDelta.ADDED, IResourceDelta.MOVED_FROM, file1.getFullPath(), null);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Creating and moving", 100);
					try {
						folder2.create(true, true, new SubProgressMonitor(m, 50));
						file1.move(file3.getFullPath(), true, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testMoveFileAddMarker() {
		try {
			verifier.addExpectedChange(folder2, IResourceDelta.ADDED, 0);
			verifier.addExpectedChange(file1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, file3.getFullPath());
			verifier.addExpectedChange(file3, IResourceDelta.ADDED, IResourceDelta.MOVED_FROM | IResourceDelta.MARKERS, file1.getFullPath(), null);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Creating and moving", 100);
					try {
						folder2.create(true, true, new SubProgressMonitor(m, 50));
						file1.move(file3.getFullPath(), true, new SubProgressMonitor(m, 50));
						file3.createMarker(IMarker.TASK);
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}

	public void testMoveModifyFile() {
		try {
			verifier.addExpectedChange(folder2, IResourceDelta.ADDED, 0);
			verifier.addExpectedChange(file1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, file3.getFullPath());
			verifier.addExpectedChange(file3, IResourceDelta.ADDED, IResourceDelta.MOVED_FROM | IResourceDelta.CONTENT, file1.getFullPath(), null);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Creating and moving", 100);
					try {
						folder2.create(true, true, new SubProgressMonitor(m, 50));
						file1.move(file3.getFullPath(), true, new SubProgressMonitor(m, 50));
						file3.setContents(getRandomContents(), IResource.NONE, getMonitor());
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}

	public void testMoveMoveFile() {
		file2 = project1.getFile("File2");
		file3 = project1.getFile("File3");

		try {
			verifier.addExpectedChange(file1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, file3.getFullPath());
			verifier.addExpectedChange(file3, IResourceDelta.ADDED, IResourceDelta.MOVED_FROM, file1.getFullPath(), null);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("moving and moving file", 100);
					try {
						file1.move(file2.getFullPath(), false, null);
						file2.move(file3.getFullPath(), false, null);
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}

	public void testMoveMoveFolder() {
		folder2 = project1.getFolder("Folder2");
		folder3 = project1.getFolder("Folder3");
		file3 = folder3.getFile(file1.getName());

		try {

			verifier.addExpectedChange(folder1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, folder3.getFullPath());
			verifier.addExpectedChange(folder3, IResourceDelta.ADDED, IResourceDelta.MOVED_FROM, folder1.getFullPath(), null);
			verifier.addExpectedChange(file1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, file3.getFullPath());
			verifier.addExpectedChange(file3, IResourceDelta.ADDED, IResourceDelta.MOVED_FROM, file1.getFullPath(), null);

			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("moving and moving folder", 100);
					try {
						folder1.move(folder2.getFullPath(), false, null);
						folder2.move(folder3.getFullPath(), false, null);
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	/**
	 * Move a project via rename.
	 * Note that the DESCRIPTION flag should be set in the delta for the
	 * destination only.
	 */
	public void testMoveProject1() {
		try {
			verifier.reset();
			verifier.addExpectedChange(project1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, project2.getFullPath());
			verifier.addExpectedChange(project1.getFile(".project"), IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, project2.getFile(".project").getFullPath());
			verifier.addExpectedChange(folder1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, project2.getFolder(folder1.getProjectRelativePath()).getFullPath());
			verifier.addExpectedChange(file1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, project2.getFile(file1.getProjectRelativePath()).getFullPath());

			verifier.addExpectedChange(project2, IResourceDelta.ADDED, IResourceDelta.OPEN | IResourceDelta.DESCRIPTION | IResourceDelta.MOVED_FROM, project1.getFullPath(), null);
			verifier.addExpectedChange(project2.getFile(".project"), IResourceDelta.ADDED, IResourceDelta.CONTENT | IResourceDelta.MOVED_FROM, project1.getFile(".project").getFullPath(), null);
			verifier.addExpectedChange(project2.getFolder(folder1.getProjectRelativePath()), IResourceDelta.ADDED, IResourceDelta.MOVED_FROM, folder1.getFullPath(), null);
			verifier.addExpectedChange(project2.getFile(file1.getProjectRelativePath()), IResourceDelta.ADDED, IResourceDelta.MOVED_FROM, file1.getFullPath(), null);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Creating and moving", 100);
					try {
						project1.move(project2.getFullPath(), IResource.NONE, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	/**
	 * Move a project via a location change only.
	 * Note that the DESCRIPTION flag should be set in the delta.
	 */
	public void testMoveProject2() {
		final IPath path = getRandomLocation();
		try {
			verifier.addExpectedChange(project1, IResourceDelta.CHANGED, IResourceDelta.DESCRIPTION);
			verifier.addExpectedChange(project1.getFile(".project"), IResourceDelta.CHANGED, IResourceDelta.CONTENT);

			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Creating and moving", 100);
					try {
						IProjectDescription desc = project1.getDescription();
						desc.setLocation(path);
						project1.move(desc, IResource.NONE, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		} finally {
			Workspace.clear(path.toFile());
		}
	}
	public void testMulti() {

		class Listener1 implements IResourceChangeListener {
			public boolean done = false;
			public void resourceChanged(IResourceChangeEvent event) {
				assertEquals("1.0", IResourceChangeEvent.POST_CHANGE, event.getType());
				done = true;
			}
		}

		class Listener2 extends Listener1 implements IResourceChangeListener {
			public void resourceChanged(IResourceChangeEvent event) {
				assertEquals("2.0", IResourceChangeEvent.POST_AUTO_BUILD, event.getType());
				done = true;
			}
		}

		Listener1 listener1 = new Listener1();
		Listener2 listener2 = new Listener2();

		getWorkspace().addResourceChangeListener(listener1, IResourceChangeEvent.POST_CHANGE);
		getWorkspace().addResourceChangeListener(listener2, IResourceChangeEvent.POST_AUTO_BUILD);
		try {
			try {
				project1.touch(getMonitor());
			} catch (CoreException e) {
				handleCoreException(e);
			}

			int i = 0;
			while (!(listener1.done && listener2.done)) {
				//timeout if the listeners are never called
				assertTrue("3.0", ++i < 600);
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
				}
			}
		} finally {
			getWorkspace().removeResourceChangeListener(listener1);
			getWorkspace().removeResourceChangeListener(listener2);
		}
	}

	public void testRemoveFile() {
		try {
			verifier.addExpectedChange(file1, IResourceDelta.REMOVED, 0);
			file1.delete(true, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testRemoveFileAndFolder() {
		try {
			verifier.addExpectedChange(folder1, IResourceDelta.REMOVED, 0);
			verifier.addExpectedChange(file1, IResourceDelta.REMOVED, 0);
			folder1.delete(true, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testReplaceFile() {
		try {
			/* change file1's contents */
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.REPLACED | IResourceDelta.CONTENT);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("Deleting and Creating", 100);
					try {
						file1.delete(true, new SubProgressMonitor(m, 50));
						file1.create(getRandomContents(), true, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}

	public void testReplaceFolderWithFolder() {
		try {
			folder2 = project1.getFolder("Folder2");
			folder3 = project1.getFolder("Folder3");
			verifier.reset();
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					file1.delete(false, null);
					folder2.create(false, true, null);
				}
			}, null);
			verifier.waitForDelta();
			verifier.reset();

			verifier.addExpectedChange(folder1, IResourceDelta.REMOVED, IResourceDelta.MOVED_TO, null, folder2.getFullPath());
			int flags = IResourceDelta.MOVED_FROM | IResourceDelta.MOVED_TO | IResourceDelta.REPLACED | IResourceDelta.CONTENT;
			verifier.addExpectedChange(folder2, IResourceDelta.CHANGED, flags, folder1.getFullPath(), folder3.getFullPath());
			verifier.addExpectedChange(folder3, IResourceDelta.ADDED, IResourceDelta.MOVED_FROM, folder2.getFullPath(), null);

			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("replace folder with folder", 100);
					try {
						folder2.move(folder3.getFullPath(), false, null);
						folder1.move(folder2.getFullPath(), false, null);
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testSetLocal() {
		try {
			verifier.reset();
			//set local on a file that is already local -- should be no change
			file1.setLocal(true, IResource.DEPTH_INFINITE, getMonitor());
			assertTrue("Unexpected notification on no change", !verifier.hasBeenNotified());

			//set non-local, still shouldn't appear in delta
			verifier.reset();
			file1.setLocal(false, IResource.DEPTH_INFINITE, getMonitor());
			assertTrue("Unexpected notification on no change", !verifier.hasBeenNotified());
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}

	public void testSwapFiles() {
		try {
			file1 = project1.getFile("File1");
			file2 = project1.getFile("File2");
			file3 = project1.getFile("File3");

			verifier.reset();
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					file1.create(new ByteArrayInputStream(new byte[] { 65 }), false, null);
					file2.create(new ByteArrayInputStream(new byte[] { 67 }), false, null);
				}
			}, null);
			verifier.waitForDelta();
			verifier.reset();

			final int flags = IResourceDelta.MOVED_FROM | IResourceDelta.MOVED_TO | IResourceDelta.REPLACED | IResourceDelta.CONTENT;
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, flags, file2.getFullPath(), file2.getFullPath());
			verifier.addExpectedChange(file2, IResourceDelta.CHANGED, flags, file1.getFullPath(), file1.getFullPath());

			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("swap files", 100);
					try {
						file1.move(file3.getFullPath(), false, null);
						file2.move(file1.getFullPath(), false, null);
						file3.move(file2.getFullPath(), false, null);
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}

	public void testSwapFolders() {
		try {
			verifier.reset();
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					folder2 = project1.getFolder("Folder2");
					folder3 = project1.getFolder("Folder3");
					file1.delete(false, null);
					folder2.create(false, true, null);
				}
			}, null);
			verifier.waitForDelta();
			verifier.reset();

			final int flags = IResourceDelta.MOVED_FROM | IResourceDelta.MOVED_TO | IResourceDelta.REPLACED | IResourceDelta.CONTENT;
			verifier.addExpectedChange(folder1, IResourceDelta.CHANGED, flags, folder2.getFullPath(), folder2.getFullPath());
			verifier.addExpectedChange(folder2, IResourceDelta.CHANGED, flags, folder1.getFullPath(), folder1.getFullPath());

			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("swap folders", 100);
					try {
						folder1.move(folder3.getFullPath(), false, null);
						folder2.move(folder1.getFullPath(), false, null);
						folder3.move(folder2.getFullPath(), false, null);
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	/**
	 * Asserts that the delta is correct for changes to team private members.
	 */
	public void testTeamPrivateChanges() {
		IWorkspace workspace = getWorkspace();
		final IFolder teamPrivateFolder = project1.getFolder("TeamPrivateFolder");
		final IFile teamPrivateFile = folder1.getFile("TeamPrivateFile");
		try {
			//create a team private folder
			verifier.reset();
			verifier.addExpectedChange(teamPrivateFolder, IResourceDelta.ADDED, 0);
			workspace.run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					teamPrivateFolder.create(true, true, getMonitor());
					teamPrivateFolder.setTeamPrivateMember(true);
				}
			}, getMonitor());
			assertDelta();
			verifier.reset();

			//create children in team private folder
			IFile fileInFolder = teamPrivateFolder.getFile("FileInPrivateFolder");
			verifier.addExpectedChange(fileInFolder, IResourceDelta.ADDED, 0);
			fileInFolder.create(getRandomContents(), true, getMonitor());
			assertDelta();
			verifier.reset();

			//modify children in team private folder
			verifier.addExpectedChange(fileInFolder, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			fileInFolder.setContents(getRandomContents(), IResource.NONE, getMonitor());
			assertDelta();
			verifier.reset();

			//delete children in team private folder
			verifier.addExpectedChange(fileInFolder, IResourceDelta.REMOVED, 0);
			fileInFolder.delete(IResource.NONE, getMonitor());
			assertDelta();
			verifier.reset();

			//delete team private folder and change some other file
			verifier.addExpectedChange(teamPrivateFolder, IResourceDelta.REMOVED, 0);
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			workspace.run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					teamPrivateFolder.delete(IResource.NONE, getMonitor());
					file1.setContents(getRandomContents(), IResource.NONE, getMonitor());
				}
			}, getMonitor());
			assertDelta();
			verifier.reset();

			//create team private file
			verifier.addExpectedChange(teamPrivateFile, IResourceDelta.ADDED, 0);
			workspace.run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					teamPrivateFile.create(getRandomContents(), true, getMonitor());
					teamPrivateFile.setTeamPrivateMember(true);
				}
			}, getMonitor());
			assertDelta();
			verifier.reset();

			//modify team private file
			verifier.addExpectedChange(teamPrivateFile, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			teamPrivateFile.setContents(getRandomContents(), IResource.NONE, getMonitor());
			assertDelta();
			verifier.reset();

			//delete team private file
			verifier.addExpectedChange(teamPrivateFile, IResourceDelta.REMOVED, 0);
			teamPrivateFile.delete(IResource.NONE, getMonitor());
			assertDelta();
			verifier.reset();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
	public void testTwoFileChanges() {
		try {
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			verifier.addExpectedChange(file2, IResourceDelta.ADDED, 0);
			getWorkspace().run(new IWorkspaceRunnable() {
				public void run(IProgressMonitor m) throws CoreException {
					m.beginTask("setting contents and creating", 100);
					try {
						file1.setContents(getRandomContents(), true, false, new SubProgressMonitor(m, 50));
						file2.create(getRandomContents(), true, new SubProgressMonitor(m, 50));
					} finally {
						m.done();
					}
				}
			}, getMonitor());
			assertDelta();
		} catch (CoreException e) {
			handleCoreException(e);
		}
	}
}
