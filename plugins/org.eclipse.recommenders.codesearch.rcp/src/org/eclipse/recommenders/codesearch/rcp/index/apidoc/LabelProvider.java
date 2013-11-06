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

import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang3.ArrayUtils.subarray;
import static org.apache.commons.lang3.StringUtils.*;

import java.util.List;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jdt.ui.text.IJavaColorConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.recommenders.codesearch.rcp.index.Fields;
import org.eclipse.recommenders.codesearch.rcp.index.searcher.SearchResult;
import org.eclipse.recommenders.internal.codesearch.rcp.CodesearchIndexPlugin;
import org.eclipse.recommenders.internal.codesearch.rcp.PreferencePage;
import org.eclipse.recommenders.rcp.JavaElementResolver;
import org.eclipse.recommenders.rcp.utils.ASTNodeUtils;
import org.eclipse.recommenders.utils.IOUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

@SuppressWarnings("restriction")
public class LabelProvider extends StyledCellLabelProvider {

    private final JavaElementLabelProvider jdtLabelProvider = new JavaElementLabelProvider();
    private MethodDeclaration astMethod;
    private TypeDeclaration astType;
    private IType jdtType;
    private List<ASTNode> statements;
    private final List<String> searchterms;
    String searchType;

    public LabelProvider(final JavaElementResolver jdtCache, final List<String> searchterms,
            String searchType, final SearchResult searchResults) {
        this.searchterms = searchterms;
        this.searchType = searchType;
    }

    /**
     * Finds all relevant statements and creates a *very* simple summary
     */
    @Override
    public void update(final ViewerCell cell)
    {
        cell.setFont(JFaceResources.getTextFont());
        final Selection s = (Selection) cell.getElement();
        astMethod = s.method;
        final String varname = s.varname;
        
        if (s.isError()) {
            cell.setText(s.exception.getMessage());
            super.update(cell);
            return;
        }
        jdtType = (IType) JavaCore.create(s.doc.get(Fields.JAVA_ELEMENT_HANDLE)).getAncestor(IJavaElement.TYPE);
        
        if (!findAstType() && !s.hasMethod() )
        {
            cell.setText("// failed to resolve method and class.");
            setCellToCommentStyle(cell);
            super.update(cell);
            return;
        }
        
        // This is a case that doesn't have an enclosing method. Generate summary only with class details
        if((!hasEnclosingMethod() || !s.hasMethod()) && astType != null ) 
        {
            if(!findTypeDeclarationStatements(astType))
            {
                cell.setText("// failed to find statements from class.");
                setCellToCommentStyle(cell);
                super.update(cell);
                return;
            }
      
            setCellText(cell);
            super.update(cell);
            return;
        }

        if (!findVarUsageStatements(varname))
        {
            if(searchType.equals(LocalExamplesProvider.METHOD_INVOCATION_SEARCH) && astMethod == ContentProvider.EMPTY)
            {
                findTypeDeclarationStatements(astType);
                setCellText(cell);
                super.update(cell);
                return;
            }
            cell.setText("// No interesting statements found.\n// Either index is outdated or method is not actually using this variable?");
            setCellToCommentStyle(cell);
            super.update(cell);
            
            return;
        }
        
        setCellText(cell);
        super.update(cell);
    }
    private boolean findAstType(){
        try {
            astType = ASTNodeSearchUtil.getTypeDeclarationNode(jdtType, SharedASTProvider.getAST(jdtType.getTypeRoot(), SharedASTProvider.WAIT_YES, null));
        } catch (JavaModelException e) {
           
        }
        catch (NullPointerException e) {
        }
        return astType!=null;
    }

    private void setCellToCommentStyle(final ViewerCell cell)
    {
        final IColorManager colorManager = JavaUI.getColorManager();
        final Color color = colorManager.getColor(IJavaColorConstants.JAVA_MULTI_LINE_COMMENT);
        final StyleRange[] ranges = { new StyleRange(0, cell.getText().length(), color, null) };
        cell.setStyleRanges(ranges);
        
    }
    private boolean findTypeDeclarationStatements(TypeDeclaration td){
        statements = Lists.newArrayList();
        td.accept(new ASTVisitor() {

            @Override
            public boolean visit(TypeDeclaration node) {
               
                statements.add(node);
                return super.visit(node);
            }
            
        });
        return true;
    }
    private boolean hasEnclosingMethod(){
      boolean ss= !((searchType.equals(LocalExamplesProvider.CLASS_FIELD_SEARCH)) 
                || (searchType.equals(LocalExamplesProvider.EXTENDED_TYPE_SEARCH))
                || (searchType.equals(LocalExamplesProvider.IMPLEENTED_TYPE_SEARCH))
                //this is a Class annotation
                || ((searchType.equals(LocalExamplesProvider.USED_ANNOTATION_SEARCH) && astMethod == ContentProvider.EMPTY))
                
              );
      return ss;
    }
    private boolean findVarUsageStatements(final String varname)
    {
        statements = Lists.newArrayList();

        if (isEmpty(varname)) {
            return false;
        }

        astMethod.accept(new ASTVisitor() {
            @Override
            public boolean visit(final SimpleName node) {
                if (node.getIdentifier().equals(varname)) {
                    collectStatement(node);
                }
                return false;
            }
            

            @Override
            public boolean visit(MethodDeclaration node) {
                // Todo : improve summaries for these searches. Currently it displays full method declaration as summary
                if(searchType.equals(LocalExamplesProvider.METHOD_PARAMETER_SEARCH)||
                        searchType.equals(LocalExamplesProvider.CHECKED_EXCEPTION_SEARCH)||
                        searchType.equals(LocalExamplesProvider.METHOD_INVOCATION_SEARCH)||
                        searchType.equals(LocalExamplesProvider.RETURN_TYPE_SEARCH)||
                        (searchType.equals(LocalExamplesProvider.USED_ANNOTATION_SEARCH))
                        ){
                    
                    statements.add(node);
                }
                return super.visit(node);
            }


            private void collectStatement(final SimpleName node)
            {
                for (ASTNode curr = node.getParent(); curr != null; curr = curr.getParent()) {
                    if (curr instanceof ExpressionStatement) {
                        statements.add(curr);
                        return;
                    } else if (curr instanceof ReturnStatement) {
                        statements.add(curr);
                        return;
                    }
                }

                final ASTNode sup = node.getParent();
                switch (sup.getNodeType()) {
                case ASTNode.CLASS_INSTANCE_CREATION:
                    final ASTNode supSup = sup.getParent();
                    switch (supSup.getNodeType()) {
                    case ASTNode.CLASS_INSTANCE_CREATION:
                    case ASTNode.ASSIGNMENT:
                        statements.add(supSup);
                        break;
                    case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
                        final ASTNode supSupSup = supSup.getParent();
                        if (supSupSup instanceof VariableDeclarationStatement) {
                            statements.add(supSupSup);
                        } else {
                            statements.add(supSup);
                        }
                    }

                    break;
                case ASTNode.RETURN_STATEMENT:
                    if (((ReturnStatement) sup).getExpression() instanceof SimpleName) {
                        break;
                    }
                    //$FALL-THROUGH$
                case ASTNode.METHOD_INVOCATION:
                case ASTNode.SUPER_METHOD_INVOCATION:
                case ASTNode.FIELD_ACCESS:
                case ASTNode.SUPER_FIELD_ACCESS:
                case ASTNode.CONSTRUCTOR_INVOCATION:
                case ASTNode.SUPER_CONSTRUCTOR_INVOCATION:
                case ASTNode.CAST_EXPRESSION:
                case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
                    statements.add(sup);
                    break;
                default:
                    break;
                }
            };
        });

        return !statements.isEmpty();
    }

    private void setCellText(final ViewerCell cell)
    {
        final StringBuilder sb = new StringBuilder();
        final List<StyleRange> ranges = newArrayList();
        
        for (final ASTNode n : statements)
        {
            sb.append(n.toString()).append(IOUtils.LINE_SEPARATOR);
        }
        final String[] split = split(sb.toString(), IOUtils.LINE_SEPARATOR);
        
        String summary = join(subarray(split, 0, 3), IOUtils.LINE_SEPARATOR);
        String header = getHeader(cell.getElement());
       
        ranges.add(new StyleRange(0, header.length(), Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY), null));
        summary = header+summary;
        
        if(CodesearchIndexPlugin.getDefault().getPreferenceStore()
                .getBoolean(PreferencePage.P_HIGHLIGHT_SUMMARY)){
            
            final String[] s = CodesearchIndexPlugin.getDefault().getPreferenceStore().getString(PreferencePage.P_HIGHLIGHT_COLOR).split(",");
            final Color color = new Color( Display.getCurrent(), Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2]));
           
    
            for (final String term : searchterms)
            {
    
                int index = header.length();
                while (true) {
                    index = indexOfIgnoreCase(summary, term, index);
                    if (index == -1) {
                        break;
                    }
                    ranges.add(new StyleRange(index, term.length(), null, color));
                    index += term.length();
                }
            }
        }
        
        cell.setFont(JFaceResources.getTextFont());
        cell.setStyleRanges(ranges.toArray(new StyleRange[0]));
        cell.setText(summary);
    }

    @Override
    protected void measure(final Event event, final Object element)
    {
        final TableItem item = (TableItem) event.item;
        final GC gc = event.gc;
        gc.setFont(item.getFont());
        final Point size = gc.textExtent("\n\n\n");
        final Table parent = item.getParent();
        final Rectangle clientArea = parent.getClientArea();
        final int clientWidth = clientArea.width;
        event.width = clientWidth - 20;
        event.height = size.y;
        final Rectangle bounds = item.getBounds();
        gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_GRAY));
        gc.drawLine(bounds.x, bounds.y, bounds.x + clientWidth, bounds.y);
        super.measure(event, element);
    }

    @Override
    public String getToolTipText(final Object element)
    {
        if (element instanceof Selection) {
            final Selection s = (Selection) element;
            if (s.method != null) {
                final Optional<TypeDeclaration> enclosingType = ASTNodeUtils.getClosestParent(s.method,
                        TypeDeclaration.class);

                return (enclosingType.isPresent() ? "class " + enclosingType.get().resolveBinding().getQualifiedName()
                        : "") + "\n" + s.method.toString();
            } else if (s.isError()) {
                return Throwables.getStackTraceAsString(s.exception);
            } else if (s.doc != null) {
                return s.doc.toString();
            }
        }
        
        return null;
    }
    
    private String getHeader(final Object element)
    {
        if (element instanceof Selection) {
            final Selection s = (Selection) element;
            if (s.method != null) {
                final Optional<TypeDeclaration> enclosingType = ASTNodeUtils.getClosestParent(s.method,
                        TypeDeclaration.class);
                String header ="";
                
                try {
                    header = (enclosingType.isPresent() ? "class " + enclosingType.get().resolveBinding().getQualifiedName()+" | method "+s.element().getElementName()
                            : "") + "\n";
                } catch (Exception e) {
                   header = "Failed to determine package info.";
                }
                return header;
            } 
        }
        
        return "";
    }

    @Override
    public org.eclipse.swt.graphics.Image getToolTipImage(final Object object)
    {
        if (object instanceof Selection) {
            final Selection s = (Selection) object;
            return jdtLabelProvider.getImage(s.element());
        }
        
        return null;
    };

    @Override
    public boolean useNativeToolTip(final Object object) {
        return true;
    }

    @Override
    public Point getToolTipShift(final Object object) {
        return new Point(5, 5);
    }

    @Override
    public int getToolTipDisplayDelayTime(final Object object) {
        return 100;
    }

    @Override
    public int getToolTipTimeDisplayed(final Object object) {
        return 5000;
    }

    @Override
    public void dispose() {
        super.dispose();
    }
}
