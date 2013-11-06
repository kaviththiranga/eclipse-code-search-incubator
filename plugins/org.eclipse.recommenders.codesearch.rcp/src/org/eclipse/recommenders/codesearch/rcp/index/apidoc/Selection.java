/**
 * Copyright (c) 2013 Kavith Thiranga Lokuhewage.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *    Kavith Thiranga Lokuhewage - initial implementation.
 */

package org.eclipse.recommenders.codesearch.rcp.index.apidoc;

import org.apache.lucene.document.Document;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.MethodDeclaration;

public class Selection {

    public final MethodDeclaration method;
    public final String varname;
    public final Document doc;
    public final Exception exception;

    public Selection(final MethodDeclaration method, final String varname, final Document doc) {
        this(method, varname, doc, null);
    }

    public Selection(final Exception e) {
        this( null, null, null, e);
    }

    protected Selection(final MethodDeclaration method, final String varname, final Document doc, final Exception e) {

        this.method = method;
        this.varname = varname;
        this.doc = doc;
        this.exception = e;
    }

    public boolean isError() {
        return exception != null;
    }
    
    public boolean hasMethod(){        
        return method!=null;
    }

    IMethod element() {
        if(method != ContentProvider.EMPTY || method!=null)
            return (IMethod) method.resolveBinding().getJavaElement();
        return null;
    }    
    
}
