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

package org.eclipse.recommenders.codesearch.rcp.index.indexer;

import org.apache.lucene.document.Document;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.recommenders.codesearch.rcp.index.Fields;
import org.eclipse.recommenders.codesearch.rcp.index.indexer.interfaces.ITryCatchBlockIndexer;

import com.google.common.base.Optional;

public class CaughtTypeIndexer implements ITryCatchBlockIndexer {

    @Override
    public void indexTryCatchBlock(final Document document, final TryStatement tryStatement,
            final CatchClause catchClause) {

        final Optional<String> opt = BindingHelper.getIdentifier(catchClause.getException().getType());
        if (opt.isPresent()) {
            CodeIndexer.addFieldToDocument(document, Fields.CAUGHT_TYPE, opt.get());
        }
    }

}
