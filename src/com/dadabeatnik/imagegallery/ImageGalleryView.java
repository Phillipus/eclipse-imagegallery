/*******************************************************************************
 * Copyright (c) 2010, 2012 Phillip Beauvoir
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Phillip Beauvoir
 *******************************************************************************/
package com.dadabeatnik.imagegallery;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.nebula.widgets.gallery.DefaultGalleryItemRenderer;
import org.eclipse.nebula.widgets.gallery.Gallery;
import org.eclipse.nebula.widgets.gallery.GalleryItem;
import org.eclipse.nebula.widgets.gallery.NoGroupRenderer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;



/**
 * The View Part for the gallery
 * 
 * @author Phillip Beauvoir
 */
public class ImageGalleryView extends ViewPart implements ISelectionListener {
    
    private static int SMALL = 64;
    private static int LARGE = 128;
    
    private List<String> fExtensions = new ArrayList<String>();
    
    /**
     * The Gallery widget
     */
    private Gallery fGallery;
    
    public ImageGalleryView() {
        fExtensions.add("bmp"); //$NON-NLS-1$
        fExtensions.add("gif"); //$NON-NLS-1$
        fExtensions.add("png"); //$NON-NLS-1$
        fExtensions.add("jpg"); //$NON-NLS-1$
        fExtensions.add("tif"); //$NON-NLS-1$
        fExtensions.add("ico"); //$NON-NLS-1$
        //fExtensions.add("icns");
        //fExtensions.add("xpm");
    }

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
    public void createPartControl(Composite parent) {
	    fGallery = new Gallery(parent, SWT.V_SCROLL);

	    // Renderers
	    NoGroupRenderer gr = new NoGroupRenderer();
	    gr.setMinMargin(2);
	    gr.setItemSize(32, 32);
	    gr.setAutoMargin(true);
	    fGallery.setGroupRenderer(gr);
	    
	    DefaultGalleryItemRenderer ir = new DefaultGalleryItemRenderer() {
	        @Override
	        protected Point getBestSize(int originalX, int originalY, int maxX, int maxY) {
	            /*
	             * Ensure smaller images are not stretched
	             */
	            Point pt = super.getBestSize(originalX, originalY, maxX, maxY);
	            if(pt.x > originalX) {
	                pt.x = originalX;
	            }
	            if(pt.y > originalY) {
                    pt.y = originalY;
                }
	            return pt;
	        }  
	    };
	    fGallery.setItemRenderer(ir);
	    
	    // Root Group
	    new GalleryItem(fGallery, SWT.NONE);
	    
	    // Listen to workbench selections
	    getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this);
	    
	    // Double clicks
	    hookDoubleClickHandler();
	    
	    // Selections
	    hookSelectionListener();

	    // Update now!
	    selectionChanged(null, getSite().getWorkbenchWindow().getSelectionService().getSelection());
	}

    /**
     * Show image details in status bar on selection
     */
    private void hookSelectionListener() {
        fGallery.addSelectionListener(new SelectionAdapter() {
	        @Override
	        public void widgetSelected(SelectionEvent e) {
	            GalleryItem item = (GalleryItem)e.item;
                String text = ""; //$NON-NLS-1$
	            if(item != null) {
	                IStorage storage = (IStorage)item.getData();
	                if(storage != null) {
	                    text += storage.getName();
	                }
	                Image image = item.getImage();
	                if(image != null) {
	                    ImageData id = image.getImageData();
	                    text += " (" + id.width + " x " + id.height + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	                }
	            }
                getViewSite().getActionBars().getStatusLineManager().setMessage(text);
	        }
	    });
    }

    /**
     * Launch system app on double click
     */
    private void hookDoubleClickHandler() {
        fGallery.addListener(SWT.MouseDoubleClick, new Listener() {
            public void handleEvent(Event event) {
                GalleryItem item = fGallery.getItem(new Point(event.x, event.y));
                if(item != null) {
                    IStorage storage = (IStorage)item.getData();
                    if(storage instanceof IFile) {
                        String path = ((IFile)storage).getLocation().toFile().getPath();
                        Program.launch(path);
                    }
                }
            }
	    });
    }

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#setFocus()
	 */
	@Override
    public void setFocus() {
	    fGallery.setFocus();
	}

	/**
	 * Render a group of objects
	 * @param objects
     * @param group
     * @param size
	 */
	private void render(Object[] objects, GalleryItem group, int size) {
        clearGroupImages();
        ((NoGroupRenderer)fGallery.getGroupRenderer()).setItemSize(size, size);
        
	    for(int i = 0; i < objects.length; i++) {
	        Object o = objects[i];
	        
	        if(o instanceof IContainer) {
	            continue;
	        }
	        
	        if(o instanceof IStorage) {
	            IStorage storage = (IStorage)o;
	            IPath path = storage.getFullPath();
                String ext = path.getFileExtension();
                if(fExtensions.contains(ext)) {
                    addThumbnail(storage, group);
                }
            }
	    }

	    fGallery.redraw();
	}
	
    /**
     * Add a thumbnail
     * @param storage
     * @param group
     */
	private void addThumbnail(IStorage storage, GalleryItem group) {
	    GalleryItem thumb = new GalleryItem(group, SWT.NONE);
	    
        // Add File
        thumb.setText(storage.getName());
        thumb.setData(storage);

        // Add image
        InputStream stream = null;
        try {
            stream = storage.getContents();
            if(stream == null) {
                return;
            }
            Image image = new Image(null, stream);
            thumb.setImage(image);
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
        finally {
            try {
                // Must close!
                if(stream != null) {
                    stream.close();
                }
            }
            catch(IOException ex) {
                ex.printStackTrace();
            }
        }
	}
	
	/**
	 * Clear old images from root group
	 */
	private void clearGroupImages() {
        if(fGallery != null && !fGallery.isDisposed() && fGallery.getItemCount() > 0) {
            GalleryItem group = fGallery.getItem(0);
            if(group != null) {
                while(group.getItemCount() > 0) {
                    GalleryItem item = group.getItem(0);
                    Image image = item.getImage();
                    if(image != null && !image.isDisposed()) {
                        image.dispose(); // Not sure if Nebula disposes of images, so we will do it.
                    }
                    group.remove(item);
                }
            }
        }
	}
	
    @Override
    public void dispose() {
        getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
        
        if(fGallery != null && !fGallery.isDisposed()) {
            clearGroupImages();
            fGallery.remove(0);
            fGallery.dispose();
            fGallery = null;
        }
        
        super.dispose();
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.ISelectionListener#selectionChanged(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
     */
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if(fGallery != null && !fGallery.isDisposed()) {
            
            if(selection instanceof IStructuredSelection) {
                Object selected = ((IStructuredSelection)selection).getFirstElement();
                int size = SMALL;
                Object[] resources = null; 
                
                // Java Package Fragment Root
                if(selected instanceof IPackageFragmentRoot) {
                    try {
                        resources = ((IPackageFragmentRoot)selected).getNonJavaResources();
                    }
                    catch(JavaModelException ex) {
                    }
                }
                
                // Java Package Fragment
                else if(selected instanceof IPackageFragment) {
                    try {
                        resources = ((IPackageFragment)selected).getNonJavaResources();
                    }
                    catch(JavaModelException ex) {
                    }
                }
                
                // Java Project
                else if(selected instanceof IJavaProject) {
                    selected = ((IJavaProject)selected).getProject(); // get the IContainer as selected item
                }
                
                // Container
                if(selected instanceof IContainer) {
                    try {
                        resources = ((IContainer)selected).members();
                    }
                    catch(CoreException ex) {
                    }
                }
                
                // Single File
                else if(selected instanceof IStorage) {
                    resources = new Object[] { selected };
                    size = LARGE;
                }
                
                if(resources != null) {
                    render(resources, fGallery.getItem(0), size);
                }
            }

            // Clear status bar
            getViewSite().getActionBars().getStatusLineManager().setMessage(""); //$NON-NLS-1$
        }
    }
}