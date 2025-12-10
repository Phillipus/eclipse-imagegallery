/*******************************************************************************
 * Copyright (c) 2010, 2025 Phillip Beauvoir
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Phillip Beauvoir
 *******************************************************************************/
package com.dadabeatnik.imagegallery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.nebula.widgets.gallery.CustomDefaultGalleryItemRenderer;
import org.eclipse.nebula.widgets.gallery.Gallery;
import org.eclipse.nebula.widgets.gallery.GalleryItem;
import org.eclipse.nebula.widgets.gallery.NoGroupRenderer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;



/**
 * The View Part for the gallery
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class ImageGalleryView extends ViewPart implements ISelectionListener {
    
    private static final int SMALL_IMAGE_SIZE = 64;
    private static final int LARGE_IMAGE_SIZE = 128;
    
    private static final Set<String> imageExtensions = new HashSet<>();
    
    private Gallery gallery;
    private NoGroupRenderer groupRenderer;
    private GalleryItem rootGroupItem;
    
    private boolean isSVGSupported;
    
    public ImageGalleryView() {
        isSVGSupported = isSvgSupported();
        
        imageExtensions.add("bmp");
        imageExtensions.add("gif");
        imageExtensions.add("png");
        imageExtensions.add("jpg");
        imageExtensions.add("tif");
        imageExtensions.add("ico");
        imageExtensions.add("svg");
        //imageExtensions.add("icns");
        //imageExtensions.add("xpm");
    }
    
    private boolean isSvgSupported() {
        String svg = """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg width="1" height="1" version="1.1" viewBox="0 0 0 0" xmlns="http://www.w3.org/2000/svg"></svg>
                """;
        try(InputStream is = new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))) {
            new ImageLoader().load(is);
        }
        catch(SWTException | IOException e) {
            return false;
        }
        
        return true;
    }

	@Override
    public void createPartControl(Composite parent) {
	    gallery = new Gallery(parent, SWT.V_SCROLL);

	    // Group renderer
	    groupRenderer = new NoGroupRenderer();
	    groupRenderer.setMinMargin(2);
	    groupRenderer.setItemSize(SMALL_IMAGE_SIZE, SMALL_IMAGE_SIZE);
	    groupRenderer.setAutoMargin(true);
	    gallery.setGroupRenderer(groupRenderer);
	    
	    // Item renderer
	    gallery.setItemRenderer(new CustomDefaultGalleryItemRenderer());
	    
	    // Root Group Item
	    rootGroupItem = new GalleryItem(gallery, SWT.NONE);
	    
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
        gallery.addSelectionListener(new SelectionAdapter() {
	        @Override
	        public void widgetSelected(SelectionEvent e) {
                String text = "";
	            GalleryItem item = (GalleryItem)e.item;
                if(item != null && item.getData() instanceof IStorage storage) {
                    text += storage.getName();
	                Image image = item.getImage();
	                if(image != null) {
	                    Rectangle r = image.getBounds();
	                    text += " (" + r.width + " x " + r.height + ")";
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
        gallery.addListener(SWT.MouseDoubleClick, event -> {
            GalleryItem item = gallery.getItem(new Point(event.x, event.y));
            if(item != null && item.getData() instanceof IFile file) {
                String path = file.getLocation().toFile().getPath();
                Program.launch(path);
            }
	    });
    }

	@Override
    public void setFocus() {
	    gallery.setFocus();
	}

	/**
	 * Render a group of objects
	 */
	private void render(Object[] objects, int size) {
        clearGroupImages();
        groupRenderer.setItemSize(size, size);
        
        if(objects != null) {
            for(Object object : objects) {
                if(object instanceof IStorage storage) {
                    String ext = storage.getFullPath().getFileExtension();
                    if(imageExtensions.contains(ext)) {
                        addThumbnail(storage);
                    }
                }
            }
        }

	    gallery.redraw();
	}
	
    /**
     * Add a thumbnail item
     */
	private void addThumbnail(IStorage storage) {
	    GalleryItem item = new GalleryItem(rootGroupItem, SWT.NONE);
	    
        // Add File
	    item.setText(storage.getName());
	    item.setData(storage);
	    
	    // Check if SVG supported
	    if("svg".equalsIgnoreCase(storage.getFullPath().getFileExtension()) && !isSVGSupported) {
	        return;
	    }

        // Add image
        try(InputStream stream = storage.getContents()) {
            Image image = new Image(getSite().getShell().getDisplay(), stream);
            item.setImage(image);
        }
        catch(Exception ex) {
            ImageGalleryPlugin.getDefault().getLog().error("Error creating image", ex);
        }
	}
	
	/**
	 * Clear old images from root group
	 */
	private void clearGroupImages() {
        if(gallery != null && !gallery.isDisposed()) {
            for(GalleryItem item : rootGroupItem.getItems()) {
                // Must dispose of image here
                Image image = item.getImage();
                if(image != null) {
                    image.dispose();
                }
                item.dispose();
            }
        }
	}
	
    @Override
    public void dispose() {
        super.dispose();

        getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
        
        if(gallery != null && !gallery.isDisposed()) {
            clearGroupImages();
            gallery.remove(0);
            gallery.dispose();
            gallery = null;
        }
        
        groupRenderer = null;
        rootGroupItem = null;
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if(gallery == null || gallery.isDisposed() || !(selection instanceof IStructuredSelection structuredSelection)) {
            return;
        }
        
        Object selected = structuredSelection.getFirstElement();
        int size = SMALL_IMAGE_SIZE;
        Object[] resources = null;

        try {
            // Java Package Fragment Root
            if(selected instanceof IPackageFragmentRoot fr) {
                resources = fr.getNonJavaResources();
            }
            // Java Package Fragment
            else if(selected instanceof IPackageFragment fragment) {
                resources = fragment.getNonJavaResources();
            }
            // Java Project
            else if(selected instanceof IJavaProject jp) {
                selected = jp.getProject(); // get the IContainer as selected item
            }

            // Container
            if(selected instanceof IContainer container) {
                resources = container.members();
            }
            // Single File
            else if(selected instanceof IStorage) {
                resources = new Object[] { selected };
                size = LARGE_IMAGE_SIZE;
            }

        }
        catch(CoreException ex) {
            ImageGalleryPlugin.getDefault().getLog().error("Error getting resources", ex);
        }

        render(resources, size);
    }
}