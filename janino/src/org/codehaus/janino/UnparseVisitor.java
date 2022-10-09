
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2010, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. The name of the author may not be used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.codehaus.janino;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.janino.util.AutoIndentWriter;

/**
 * A visitor that unparses (un-compiles) an AST to a {@link Writer}. See
 * {@link #main(String[])} for a usage example.
 */
@SuppressWarnings({ "rawtypes", "unchecked" }) public
class UnparseVisitor implements Visitor.ComprehensiveVisitor {

    private final AutoIndentWriter aiw;
    private final PrintWriter      pw;

    /**
     * Testing of parsing/unparsing.
     * <p>
     * Reads compilation units from the files named on the command line
     * and unparses them to {@link System#out}.
     */
    public static void
    main(String[] args) throws Exception {

        Writer w = new BufferedWriter(new OutputStreamWriter(System.out));
        for (int i = 0; i < args.length; ++i) {
            String fileName = args[i];

            // Parse each compilation unit.
            FileReader           r = new FileReader(fileName);
            Java.CompilationUnit cu;
            try {
                cu = new Parser(new Scanner(fileName, r)).parseCompilationUnit();
            } finally {
                r.close();
            }

            // Unparse each compilation unit.
            UnparseVisitor.unparse(cu, w);
        }
        w.flush();
    }

    /**
     * Unparse the given {@link Java.CompilationUnit} to the given {@link Writer}.
     */
    public static void
    unparse(Java.CompilationUnit cu, Writer w) {
        UnparseVisitor uv = new UnparseVisitor(w);
        uv.unparseCompilationUnit(cu);
        uv.close();
    }

    public
    UnparseVisitor(Writer w) {
        this.aiw = new AutoIndentWriter(w);
        this.pw  = new PrintWriter(this.aiw, true);
    }

    /**
     * Flushes all generated code.
     */
    public void
    close() {
        this.pw.flush();
    }

    public void
    unparseCompilationUnit(Java.CompilationUnit cu) {
        if (cu.optionalPackageDeclaration != null) {
            this.pw.println();
            this.pw.println("package " + cu.optionalPackageDeclaration.packageName + ';');
        }
        if (!cu.importDeclarations.isEmpty()) {
            this.pw.println();
            for (Iterator it = cu.importDeclarations.iterator(); it.hasNext();) {
                ((Java.CompilationUnit.ImportDeclaration) it.next()).accept(this);
            }
        }
        for (Iterator it = cu.packageMemberTypeDeclarations.iterator(); it.hasNext();) {
            this.pw.println();
            this.unparseTypeDeclaration((Java.PackageMemberTypeDeclaration) it.next());
            this.pw.println();
        }
    }

    @Override public void
    visitSingleTypeImportDeclaration(Java.CompilationUnit.SingleTypeImportDeclaration stid) {
        this.pw.println("import " + Java.join(stid.identifiers, ".") + ';');
    }

    @Override public void
    visitTypeImportOnDemandDeclaration(Java.CompilationUnit.TypeImportOnDemandDeclaration tiodd) {
        this.pw.println("import " + Java.join(tiodd.identifiers, ".") + ".*;");
    }

    @Override public void
    visitSingleStaticImportDeclaration(Java.CompilationUnit.SingleStaticImportDeclaration ssid) {
        this.pw.println("import static " + Java.join(ssid.identifiers, ".") + ';');
    }

    @Override public void
    visitStaticImportOnDemandDeclaration(Java.CompilationUnit.StaticImportOnDemandDeclaration siodd) {
        this.pw.println("import static " + Java.join(siodd.identifiers, ".") + ".*;");
    }

    @Override public void
    visitLocalClassDeclaration(Java.LocalClassDeclaration lcd) {
        this.unparseNamedClassDeclaration(lcd);
    }

    @Override public void
    visitMemberClassDeclaration(Java.MemberClassDeclaration mcd) {
        this.unparseNamedClassDeclaration(mcd);
    }

    @Override public void
    visitMemberInterfaceDeclaration(Java.MemberInterfaceDeclaration mid) {
        this.unparseInterfaceDeclaration(mid);
    }

    @Override public void
    visitPackageMemberClassDeclaration(Java.PackageMemberClassDeclaration pmcd) {
        this.unparseNamedClassDeclaration(pmcd);
    }

    @Override    public void
    visitPackageMemberInterfaceDeclaration(Java.PackageMemberInterfaceDeclaration pmid) {
        this.unparseInterfaceDeclaration(pmid);
    }

    @Override public void
    visitConstructorDeclarator(Java.ConstructorDeclarator cd) {
        this.unparseDocComment(cd);
        this.unparseAnnotations(cd.modifiersAndAnnotations.annotations);
        this.unparseModifiers(cd.modifiersAndAnnotations.modifiers);
        Java.ClassDeclaration declaringClass = cd.getDeclaringClass();
        this.pw.print(
            declaringClass instanceof Java.NamedClassDeclaration
            ? ((Java.NamedClassDeclaration) declaringClass).name
            : "UNNAMED"
        );
        this.unparseFunctionDeclaratorRest(cd);
        this.pw.print(' ');
        if (cd.optionalConstructorInvocation != null) {
            this.pw.println('{');
            this.pw.print(AutoIndentWriter.INDENT);
            this.unparseBlockStatement(cd.optionalConstructorInvocation);
            this.pw.println(';');

            if (!cd.optionalStatements.isEmpty()) {
                this.pw.println();
                this.unparseStatements(cd.optionalStatements);
            }
            this.pw.print(AutoIndentWriter.UNINDENT + "}");
        } else
        if (cd.optionalStatements.isEmpty()) {
            this.pw.print("{}");
        } else
        {
            this.pw.println('{');
            this.pw.print(AutoIndentWriter.INDENT);
            this.unparseStatements(cd.optionalStatements);
            this.pw.print(AutoIndentWriter.UNINDENT + "}");
        }
    }

    @Override public void
    visitMethodDeclarator(Java.MethodDeclarator md) {
        this.unparseDocComment(md);
        this.unparseAnnotations(md.modifiersAndAnnotations.annotations);
        this.unparseModifiers(md.modifiersAndAnnotations.modifiers);
        this.unparseType(md.type);
        this.pw.print(' ' + md.name);
        this.unparseFunctionDeclaratorRest(md);
        if (md.optionalStatements == null) {
            this.pw.print(';');
        } else
        if (md.optionalStatements.isEmpty()) {
            this.pw.print(" {}");
        } else
        {
            this.pw.println(" {");
            this.pw.print(AutoIndentWriter.INDENT);
            this.unparseStatements(md.optionalStatements);
            this.pw.print(AutoIndentWriter.UNINDENT);
            this.pw.print('}');
        }
    }

    @Override public void
    visitFieldDeclaration(Java.FieldDeclaration fd) {
        this.unparseDocComment(fd);
        this.unparseAnnotations(fd.modifiersAndAnnotations.annotations);
        this.unparseModifiers(fd.modifiersAndAnnotations.modifiers);
        this.unparseType(fd.type);
        this.pw.print(' ');
        for (int i = 0; i < fd.variableDeclarators.length; ++i) {
            if (i > 0) this.pw.print(", ");
            this.unparseVariableDeclarator(fd.variableDeclarators[i]);
        }
        this.pw.print(';');
    }

    @Override public void
    visitInitializer(Java.Initializer i) {
        if (i.statiC) this.pw.print("static ");
        this.unparseBlockStatement(i.block);
    }

    @Override public void
    visitBlock(Java.Block b) {
        if (b.statements.isEmpty()) {
            this.pw.print("{}");
            return;
        }
        this.pw.println('{');
        this.pw.print(AutoIndentWriter.INDENT);
        this.unparseStatements(b.statements);
        this.pw.print(AutoIndentWriter.UNINDENT + "}");
    }

    private void
    unparseStatements(List statements) {

        int state = -1;
        for (Iterator it = statements.iterator(); it.hasNext();) {
            Java.BlockStatement bs = (Java.BlockStatement) it.next();
            int                 x  = (
                bs instanceof Java.Block                             ? 1 :
                bs instanceof Java.LocalClassDeclarationStatement    ? 2 :
                bs instanceof Java.LocalVariableDeclarationStatement ? 3 :
                bs instanceof Java.SynchronizedStatement             ? 4 :
                99
            );
            if (state != -1 && state != x) this.pw.println(AutoIndentWriter.CLEAR_TABULATORS);
            state = x;

            this.unparseBlockStatement(bs);
            this.pw.println();
        }
    }

    @Override public void
    visitBreakStatement(Java.BreakStatement bs) {
        this.pw.print("break");
        if (bs.optionalLabel != null) this.pw.print(' ' + bs.optionalLabel);
        this.pw.print(';');
    }

    @Override public void
    visitContinueStatement(Java.ContinueStatement cs) {
        this.pw.print("continue");
        if (cs.optionalLabel != null) this.pw.print(' ' + cs.optionalLabel);
        this.pw.print(';');
    }

    @Override public void
    visitAssertStatement(Java.AssertStatement as) {
        this.pw.print("assert ");
        this.unparse(as.expression1);
        if (as.optionalExpression2 != null) {
            this.pw.print(" : ");
            this.unparse(as.optionalExpression2);
        }
        this.pw.print(';');
    }

    @Override public void
    visitDoStatement(Java.DoStatement ds) {
        this.pw.print("do ");
        this.unparseBlockStatement(ds.body);
        this.pw.print("while (");
        this.unparse(ds.condition);
        this.pw.print(");");
    }

    @Override public void
    visitEmptyStatement(Java.EmptyStatement es) {
        this.pw.print(';');
    }

    @Override public void
    visitExpressionStatement(Java.ExpressionStatement es) {
        this.unparse(es.rvalue);
        this.pw.print(';');
    }

    @Override public void
    visitForStatement(Java.ForStatement fs) {
        this.pw.print("for (");
        if (fs.optionalInit != null) {
            this.unparseBlockStatement(fs.optionalInit);
        } else {
            this.pw.print(';');
        }
        if (fs.optionalCondition != null) {
            this.pw.print(' ');
            this.unparse(fs.optionalCondition);
        }
        this.pw.print(';');
        if (fs.optionalUpdate != null) {
            this.pw.print(' ');
            for (int i = 0; i < fs.optionalUpdate.length; ++i) {
                if (i > 0) this.pw.print(", ");
                this.unparse(fs.optionalUpdate[i]);
            }
        }
        this.pw.print(") ");
        this.unparseBlockStatement(fs.body);
    }

    @Override public void
    visitIfStatement(Java.IfStatement is) {
        this.pw.print("if (");
        this.unparse(is.condition);
        this.pw.print(") ");
        this.unparseBlockStatement(is.thenStatement);
        if (is.optionalElseStatement != null) {
            this.pw.println(" else");
            this.unparseBlockStatement(is.optionalElseStatement);
        }
    }

    @Override public void
    visitLabeledStatement(Java.LabeledStatement ls) {
        this.pw.println(ls.label + ':');
        this.unparseBlockStatement(ls.body);
    }

    @Override public void
    visitLocalClassDeclarationStatement(Java.LocalClassDeclarationStatement lcds) {
        this.unparseTypeDeclaration(lcds.lcd);
    }

    @Override public void
    visitLocalVariableDeclarationStatement(Java.LocalVariableDeclarationStatement lvds) {
        this.unparseAnnotations(lvds.modifiersAndAnnotations.annotations);
        this.unparseModifiers(lvds.modifiersAndAnnotations.modifiers);
        this.unparseType(lvds.type);
        this.pw.print(' ');
        this.pw.print(AutoIndentWriter.TABULATOR);
        this.unparseVariableDeclarator(lvds.variableDeclarators[0]);
        for (int i = 1; i < lvds.variableDeclarators.length; ++i) {
            this.pw.print(", ");
            this.unparseVariableDeclarator(lvds.variableDeclarators[i]);
        }
        this.pw.print(';');
    }

    @Override public void
    visitReturnStatement(Java.ReturnStatement rs) {
        this.pw.print("return");
        if (rs.optionalReturnValue != null) {
            this.pw.print(' ');
            this.unparse(rs.optionalReturnValue);
        }
        this.pw.print(';');
    }

    @Override public void
    visitSwitchStatement(Java.SwitchStatement ss) {
        this.pw.print("switch (");
        this.unparse(ss.condition);
        this.pw.println(") {");
        for (Iterator it = ss.sbsgs.iterator(); it.hasNext();) {
            Java.SwitchStatement.SwitchBlockStatementGroup sbgs = (
                (Java.SwitchStatement.SwitchBlockStatementGroup) it.next()
            );
            this.pw.print(AutoIndentWriter.UNINDENT);
            try {
                for (Iterator it2 = sbgs.caseLabels.iterator(); it2.hasNext();) {
                    Java.Rvalue rv = (Java.Rvalue) it2.next();
                    this.pw.print("case ");
                    this.unparse(rv);
                    this.pw.println(':');
                }
                if (sbgs.hasDefaultLabel) this.pw.println("default:");
            } finally {
                this.pw.print(AutoIndentWriter.INDENT);
            }
            for (Iterator it2 = sbgs.blockStatements.iterator(); it2.hasNext();) {
                this.unparseBlockStatement((Java.BlockStatement) it2.next());
                this.pw.println();
            }
        }
        this.pw.print('}');
    }

    @Override public void
    visitSynchronizedStatement(Java.SynchronizedStatement ss) {
        this.pw.print("synchronized (");
        this.unparse(ss.expression);
        this.pw.print(") ");
        this.unparseBlockStatement(ss.body);
    }

    @Override public void
    visitThrowStatement(Java.ThrowStatement ts) {
        this.pw.print("throw ");
        this.unparse(ts.expression);
        this.pw.print(';');
    }

    @Override public void
    visitTryStatement(Java.TryStatement ts) {
        this.pw.print("try ");
        this.unparseBlockStatement(ts.body);
        for (Iterator it = ts.catchClauses.iterator(); it.hasNext();) {
            Java.CatchClause cc = (Java.CatchClause) it.next();
            this.pw.print(" catch (");
            this.unparseFormalParameter(cc.caughtException);
            this.pw.print(") ");
            this.unparseBlockStatement(cc.body);
        }
        if (ts.optionalFinally != null) {
            this.pw.print(" finally ");
            this.unparseBlockStatement(ts.optionalFinally);
        }
    }

    @Override public void
    visitWhileStatement(Java.WhileStatement ws) {
        this.pw.print("while (");
        this.unparse(ws.condition);
        this.pw.print(") ");
        this.unparseBlockStatement(ws.body);
    }

    public void
    unparseVariableDeclarator(Java.VariableDeclarator vd) {
        this.pw.print(vd.name);
        for (int i = 0; i < vd.brackets; ++i) this.pw.print("[]");
        if (vd.optionalInitializer != null) {
            this.pw.print(" = ");
            this.unparseArrayInitializerOrRvalue(vd.optionalInitializer);
        }
    }

    public void
    unparseFormalParameter(Java.FunctionDeclarator.FormalParameter fp) {
        if (fp.finaL) this.pw.print("final ");
        this.unparseType(fp.type);
        this.pw.print(" " + AutoIndentWriter.TABULATOR + fp.name);
    }

    @Override public void
    visitMethodInvocation(Java.MethodInvocation mi) {
        if (mi.optionalTarget != null) {
            this.unparseLhs(mi.optionalTarget, ".");
            this.pw.print('.');
        }
        this.pw.print(mi.methodName);
        this.unparseFunctionInvocationArguments(mi.arguments);
    }

    @Override public void
    visitAlternateConstructorInvocation(Java.AlternateConstructorInvocation aci) {
        this.pw.print("this");
        this.unparseFunctionInvocationArguments(aci.arguments);
    }

    @Override public void
    visitSuperConstructorInvocation(Java.SuperConstructorInvocation sci) {
        if (sci.optionalQualification != null) {
            this.unparseLhs(sci.optionalQualification, ".");
            this.pw.print('.');
        }
        this.pw.print("super");
        this.unparseFunctionInvocationArguments(sci.arguments);
    }

    @Override public void
    visitNewClassInstance(Java.NewClassInstance nci) {
        if (nci.optionalQualification != null) {
            this.unparseLhs(nci.optionalQualification, ".");
            this.pw.print('.');
        }
        this.pw.print("new " + nci.type.toString());
        this.unparseFunctionInvocationArguments(nci.arguments);
    }

    @Override public void
    visitAssignment(Java.Assignment a) {
        this.unparseLhs(a.lhs, a.operator);
        this.pw.print(' ' + a.operator + ' ');
        this.unparseRhs(a.rhs, a.operator);
    }

    @Override public void
    visitAmbiguousName(Java.AmbiguousName an) { this.pw.print(an.toString()); }

    @Override public void
    visitArrayAccessExpression(Java.ArrayAccessExpression aae) {
        this.unparseLhs(aae.lhs, "[ ]");
        this.pw.print('[');
        this.unparse(aae.index);
        this.pw.print(']');
    }

    @Override public void
    visitArrayLength(Java.ArrayLength al) {
        this.unparseLhs(al.lhs, ".");
        this.pw.print(".length");
    }

    @Override public void
    visitArrayType(Java.ArrayType at) {
        this.unparseType(at.componentType);
        this.pw.print("[]");
    }

    @Override public void
    visitBasicType(Java.BasicType bt) {
        this.pw.print(bt.toString());
    }

    @Override public void
    visitBinaryOperation(Java.BinaryOperation bo) {
        this.unparseLhs(bo.lhs, bo.op);
        this.pw.print(' ' + bo.op + ' ');
        this.unparseRhs(bo.rhs, bo.op);
    }

    @Override public void
    visitCast(Java.Cast c) {
        this.pw.print('(');
        this.unparseType(c.targetType);
        this.pw.print(") ");
        this.unparseRhs(c.value, "cast");
    }

    @Override public void
    visitClassLiteral(Java.ClassLiteral cl) {
        this.unparseType(cl.type);
        this.pw.print(".class");
    }

    @Override public void
    visitConditionalExpression(Java.ConditionalExpression ce) {
        this.unparseLhs(ce.lhs, "?:");
        this.pw.print(" ? ");
        this.unparseLhs(ce.mhs, "?:");
        this.pw.print(" : ");
        this.unparseRhs(ce.rhs, "?:");
    }

    @Override public void
    visitCrement(Java.Crement c) {
        if (c.pre) {
            this.pw.print(c.operator);
            this.unparseUnaryOperation(c.operand, c.operator + "x");
        } else
        {
            this.unparseUnaryOperation(c.operand, "x" + c.operator);
            this.pw.print(c.operator);
        }
    }

    @Override public void
    visitFieldAccess(Java.FieldAccess fa) {
        this.unparseLhs(fa.lhs, ".");
        this.pw.print('.' + fa.field.getName());
    }

    @Override public void
    visitFieldAccessExpression(Java.FieldAccessExpression fae) {
        this.unparseLhs(fae.lhs, ".");
        this.pw.print('.' + fae.fieldName);
    }

    @Override public void
    visitSuperclassFieldAccessExpression(Java.SuperclassFieldAccessExpression scfae) {
        if (scfae.optionalQualification != null) {
            this.unparseType(scfae.optionalQualification);
            this.pw.print(".super." + scfae.fieldName);
        } else
        {
            this.pw.print("super." + scfae.fieldName);
        }
    }

    @Override public void
    visitInstanceof(Java.Instanceof io) {
        this.unparseLhs(io.lhs, "instanceof");
        this.pw.print(" instanceof ");
        this.unparseType(io.rhs);
    }

    @Override public void visitIntegerLiteral(Java.IntegerLiteral il)              { this.pw.print(il.value); }
    @Override public void visitFloatingPointLiteral(Java.FloatingPointLiteral fpl) { this.pw.print(fpl.value); }
    @Override public void visitBooleanLiteral(Java.BooleanLiteral bl)              { this.pw.print(bl.value); }
    @Override public void visitCharacterLiteral(Java.CharacterLiteral cl)          { this.pw.print(cl.value); }
    @Override public void visitStringLiteral(Java.StringLiteral sl)                { this.pw.print(sl.value); }
    @Override public void visitNullLiteral(Java.NullLiteral nl)                    { this.pw.print(nl.value); }
    @Override public void visitLocalVariableAccess(Java.LocalVariableAccess lva)   { this.pw.print(lva.toString()); }

    @Override public void
    visitNewArray(Java.NewArray na) {
        this.pw.print("new ");
        this.unparseType(na.type);
        for (int i = 0; i < na.dimExprs.length; ++i) {
            this.pw.print('[');
            this.unparse(na.dimExprs[i]);
            this.pw.print(']');
        }
        for (int i = 0; i < na.dims; ++i) {
            this.pw.print("[]");
        }
    }

    @Override public void
    visitNewInitializedArray(Java.NewInitializedArray nai) {
        this.pw.print("new ");
        this.unparseType(nai.arrayType);
        this.pw.print(" ");
        this.unparseArrayInitializerOrRvalue(nai.arrayInitializer);
    }

    @Override public void
    visitPackage(Java.Package p) { this.pw.print(p.toString()); }

    @Override public void
    visitParameterAccess(Java.ParameterAccess pa) { this.pw.print(pa.toString()); }

    @Override public void
    visitQualifiedThisReference(Java.QualifiedThisReference qtr) {
        this.unparseType(qtr.qualification);
        this.pw.print(".this");
    }

    @Override public void
    visitReferenceType(Java.ReferenceType rt) { this.pw.print(rt.toString()); }

    @Override public void
    visitRvalueMemberType(Java.RvalueMemberType rmt) { this.pw.print(rmt.toString()); }

    @Override public void
    visitSimpleType(Java.SimpleType st) { this.pw.print(st.toString()); }

    @Override public void
    visitSuperclassMethodInvocation(Java.SuperclassMethodInvocation smi) {
        this.pw.print("super." + smi.methodName);
        this.unparseFunctionInvocationArguments(smi.arguments);
    }

    @Override public void
    visitThisReference(Java.ThisReference tr) { this.pw.print("this"); }

    @Override public void
    visitUnaryOperation(Java.UnaryOperation uo) {
        this.pw.print(uo.operator);
        this.unparseUnaryOperation(uo.operand, uo.operator + "x");
    }

    @Override public void
    visitParenthesizedExpression(Java.ParenthesizedExpression pe) {
        this.pw.print('(');
        this.unparse(pe.value);
        this.pw.print(')');
    }

    // Helpers

    private void
    unparseBlockStatement(Java.BlockStatement blockStatement) { blockStatement.accept(this); }

    private void
    unparseTypeDeclaration(Java.TypeDeclaration typeDeclaration) { typeDeclaration.accept(this); }

    private void
    unparseType(Java.Type type) { ((Java.Atom) type).accept(this); }

    private void
    unparse(Java.Atom operand) { operand.accept(this); }

    /**
     * Iff the <code>operand</code> is unnatural for the <code>unaryOperator</code>, enclose the
     * <code>operand</code> in parentheses. Example: "a+b" is an unnatural operand for unary "!x".
     *
     * @param unaryOperator ++x --x +x -x ~x !x x++ x--
     */
    private void
    unparseUnaryOperation(Java.Rvalue operand, String unaryOperator) {
        int cmp = UnparseVisitor.comparePrecedence(unaryOperator, operand);
        this.unparse(operand, cmp < 0);
    }

    /**
     * Iff the <code>lhs</code> is unnatural for the <code>binaryOperator</code>, enclose the
     * <code>lhs</code> in parentheses. Example: "a+b" is an unnatural lhs for operator "*".
     *
     * @param binaryOperator = +=... ?: || && | ^ & == != < > <= >= instanceof << >> >>> + - * / % cast
     */
    private void
    unparseLhs(Java.Atom lhs, String binaryOperator) {
        int cmp = UnparseVisitor.comparePrecedence(binaryOperator, lhs);
        this.unparse(lhs, cmp < 0 || (cmp == 0 && UnparseVisitor.isLeftAssociate(binaryOperator)));
    }


    /**
     * Iff the <code>rhs</code> is unnatural for the <code>binaryOperator</code>, enclose the
     * <code>rhs</code> in parentheses. Example: "a+b" is an unnatural rhs for operator "*".
     */
    private void
    unparseRhs(Java.Rvalue rhs, String binaryOperator) {
        int cmp = UnparseVisitor.comparePrecedence(binaryOperator, rhs);
        this.unparse(rhs, cmp < 0 || (cmp == 0 && UnparseVisitor.isRightAssociate(binaryOperator)));
    }

    private void
    unparse(Java.Atom operand, boolean natural) {
        if (!natural) this.pw.print("((( ");
        this.unparse(operand);
        if (!natural) this.pw.print(" )))");
    }

    /**
     * Return true iff operator is right associative e.g. <code>a = b = c</code> evaluates as
     * <code>a = (b = c)</code>.
     *
     * @return Return true iff operator is right associative
     */
    private static boolean
    isRightAssociate(String op) { return UnparseVisitor.RIGHT_ASSOCIATIVE_OPERATORS.contains(op); }

    /**
     * Return true iff operator is left associative e.g. <code>a - b - c</code> evaluates as
     * <code>(a - b) - c</code>.
     *
     * @return Return true iff operator is left associative
     */
    private static boolean
    isLeftAssociate(String op) { return UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS.contains(op); }

    /**
     * Returns a value
     * <ul>
     *   <li>&lt; 0 iff the <code>operator</code> has lower precedence than the <code>operand</code>
     *   <li>==; 0 iff the <code>operator</code> has equal precedence than the <code>operand</code>
     *   <li>&gt; 0 iff the <code>operator</code> has higher precedence than the <code>operand</code>
     * </ul>
     */
    private static int
    comparePrecedence(String operator, Java.Atom operand) {
        if (operand instanceof Java.BinaryOperation) {
            return (
                UnparseVisitor.getOperatorPrecedence(operator)
                - UnparseVisitor.getOperatorPrecedence(((Java.BinaryOperation) operand).op)
            );
        } else
        if (operand instanceof Java.UnaryOperation) {
            return (
                UnparseVisitor.getOperatorPrecedence(operator)
                - UnparseVisitor.getOperatorPrecedence(((Java.UnaryOperation) operand).operator + "x")
            );
        } else
        if (operand instanceof Java.ConditionalExpression) {
            return UnparseVisitor.getOperatorPrecedence(operator) - UnparseVisitor.getOperatorPrecedence("?:");
        } else
        if (operand instanceof Java.Instanceof) {
            return UnparseVisitor.getOperatorPrecedence(operator) - UnparseVisitor.getOperatorPrecedence("instanceof");
        } else
        if (operand instanceof Java.Cast) {
            return UnparseVisitor.getOperatorPrecedence(operator) - UnparseVisitor.getOperatorPrecedence("cast");
        } else
        if (operand instanceof Java.MethodInvocation || operand instanceof Java.FieldAccess) {
            return UnparseVisitor.getOperatorPrecedence(operator) - UnparseVisitor.getOperatorPrecedence(".");
        } else
        if (operand instanceof Java.NewArray) {
            return UnparseVisitor.getOperatorPrecedence(operator) - UnparseVisitor.getOperatorPrecedence("new");
        } else
        if (operand instanceof Java.Crement) {
            Java.Crement c = (Java.Crement) operand;
            return (
                UnparseVisitor.getOperatorPrecedence(operator)
                - UnparseVisitor.getOperatorPrecedence(c.pre ? c.operator + "x" : "x" + c.operator)
            );
        } else
        {
            // All other rvalues (e.g. literal) have higher precedence than any operator.
            return -1;
        }
    }

    private static int
    getOperatorPrecedence(String operator) {
        return ((Integer) UnparseVisitor.OPERATOR_PRECEDENCE.get(operator)).intValue();
    }

    private static final Set LEFT_ASSOCIATIVE_OPERATORS  = new HashSet();
    private static final Set RIGHT_ASSOCIATIVE_OPERATORS = new HashSet();
    private static final Set UNARY_OPERATORS             = new HashSet();
    private static final Map OPERATOR_PRECEDENCE         = new HashMap();
    static {
        Object[] ops = {
            UnparseVisitor.RIGHT_ASSOCIATIVE_OPERATORS, "=", "*=", "/=", "%=", "+=", "-=", "<<=", ">>=", ">>>=",
                                                        "&=", "^=", "|=", // SUPPRESS CHECKSTYLE WrapAndIndent
            UnparseVisitor.RIGHT_ASSOCIATIVE_OPERATORS, "?:",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "||",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "&&",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "|",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "^",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "&",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "==", "!=",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "<", ">", "<=", ">=", "instanceof",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "<<", ">>", ">>>",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "+", "-",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "*", "/", "%",
            UnparseVisitor.RIGHT_ASSOCIATIVE_OPERATORS, "cast",
            UnparseVisitor.UNARY_OPERATORS,             "++x", "--x", "+x", "-x", "~x", "!x",
            UnparseVisitor.UNARY_OPERATORS,             "x++", "x--",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  "new",
            UnparseVisitor.LEFT_ASSOCIATIVE_OPERATORS,  ".", "[ ]",
        };
        int precedence = 0;
        LOOP1: for (int i = 0;;) {
            Set     s  = (Set) ops[i++];
            Integer pi = new Integer(++precedence);
            for (;;) {
                if (i == ops.length) break LOOP1;
                if (!(ops[i] instanceof String)) break;
                String op = (String) ops[i++];
                s.add(op);
                UnparseVisitor.OPERATOR_PRECEDENCE.put(op, pi);
            }
        }
    }

    private void
    unparseNamedClassDeclaration(Java.NamedClassDeclaration ncd) {
        this.unparseDocComment(ncd);
        this.unparseAnnotations(ncd.getModifiersAndAnnotations().annotations);
        this.unparseModifiers(ncd.getModifiersAndAnnotations().modifiers);
        this.pw.print("class " + ncd.name);
        if (ncd.optionalExtendedType != null) {
            this.pw.print(" extends ");
            this.unparseType(ncd.optionalExtendedType);
        }
        if (ncd.implementedTypes.length > 0) this.pw.print(" implements " + Java.join(ncd.implementedTypes, ", "));
        this.pw.println(" {");
        this.pw.print(AutoIndentWriter.INDENT);
        this.unparseClassDeclarationBody(ncd);
        this.pw.print(AutoIndentWriter.UNINDENT + "}");
    }

    private void
    unparseArrayInitializerOrRvalue(Java.ArrayInitializerOrRvalue aiorv) {
        if (aiorv instanceof Java.Rvalue) {
            this.unparse((Java.Rvalue) aiorv);
        } else
        if (aiorv instanceof Java.ArrayInitializer) {
            Java.ArrayInitializer ai = (Java.ArrayInitializer) aiorv;
            if (ai.values.length == 0) {
                this.pw.print("{}");
            } else
            {
                this.pw.print("{ ");
                this.unparseArrayInitializerOrRvalue(ai.values[0]);
                for (int i = 1; i < ai.values.length; ++i) {
                    this.pw.print(", ");
                    this.unparseArrayInitializerOrRvalue(ai.values[i]);
                }
                this.pw.print(" }");
            }
        } else
        {
            throw new JaninoRuntimeException(
                "Unexpected array initializer or rvalue class "
                + aiorv.getClass().getName()
            );
        }
    }

    @Override public void
    visitAnonymousClassDeclaration(Java.AnonymousClassDeclaration acd) {
        this.unparseType(acd.baseType);
        this.pw.println(" {");
        this.pw.print(AutoIndentWriter.INDENT);
        this.unparseClassDeclarationBody(acd);
        this.pw.print(AutoIndentWriter.UNINDENT + "}");
    }
    @Override public void
    visitNewAnonymousClassInstance(Java.NewAnonymousClassInstance naci) {
        if (naci.optionalQualification != null) {
            this.unparseLhs(naci.optionalQualification, ".");
            this.pw.print('.');
        }
        this.pw.print("new " + naci.anonymousClassDeclaration.baseType.toString() + '(');
        for (int i = 0; i < naci.arguments.length; ++i) {
            if (i > 0) this.pw.print(", ");
            this.unparse(naci.arguments[i]);
        }
        this.pw.println(") {");
        this.pw.print(AutoIndentWriter.INDENT);
        this.unparseClassDeclarationBody(naci.anonymousClassDeclaration);
        this.pw.print(AutoIndentWriter.UNINDENT + "}");
    }
    // Multi-line!
    private void
    unparseClassDeclarationBody(Java.ClassDeclaration cd) {
        for (Iterator it = cd.constructors.iterator(); it.hasNext();) {
            this.pw.println();
            ((Java.ConstructorDeclarator) it.next()).accept(this);
            this.pw.println();
        }
        this.unparseAbstractTypeDeclarationBody(cd);
        for (Iterator it = cd.variableDeclaratorsAndInitializers.iterator(); it.hasNext();) {
            this.pw.println();
            ((Java.TypeBodyDeclaration) it.next()).accept(this);
            this.pw.println();
        }
    }
    private void
    unparseInterfaceDeclaration(Java.InterfaceDeclaration id) {
        this.unparseDocComment(id);
        this.unparseAnnotations(id.getModifiersAndAnnotations().annotations);
        this.unparseModifiers(id.getModifiersAndAnnotations().modifiers);
        //make sure we print "interface", even if it wasn't in the modifiers
        if ((id.getModifiersAndAnnotations().modifiers & Mod.INTERFACE) == 0) {
            this.pw.print("interface ");
        }
        this.pw.print(id.name);
        if (id.extendedTypes.length > 0) this.pw.print(" extends " + Java.join(id.extendedTypes, ", "));
        this.pw.println(" {");
        this.pw.print(AutoIndentWriter.INDENT);
        this.unparseAbstractTypeDeclarationBody(id);
        for (Iterator it = id.constantDeclarations.iterator(); it.hasNext();) {
            ((Java.TypeBodyDeclaration) it.next()).accept(this);
            this.pw.println();
        }
        this.pw.print(AutoIndentWriter.UNINDENT + "}");
    }

    // Multi-line!
    private void
    unparseAbstractTypeDeclarationBody(Java.AbstractTypeDeclaration atd) {
        for (Iterator it = atd.getMethodDeclarations().iterator(); it.hasNext();) {
            this.pw.println();
            ((Java.MethodDeclarator) it.next()).accept(this);
            this.pw.println();
        }
        for (Iterator it = atd.getMemberTypeDeclarations().iterator(); it.hasNext();) {
            this.pw.println();
            ((Java.TypeBodyDeclaration) it.next()).accept(this);
            this.pw.println();
        }
    }
    private void
    unparseFunctionDeclaratorRest(Java.FunctionDeclarator fd) {
        boolean big = fd.formalParameters.length >= 4;
        this.pw.print('(');
        if (big) { this.pw.println(); this.pw.print(AutoIndentWriter.INDENT); }
        for (int i = 0; i < fd.formalParameters.length; ++i) {
            if (i > 0) {
                if (big) {
                    this.pw.println(',');
                } else
                {
                    this.pw.print(", ");
                }
            }
            this.unparseFormalParameter(fd.formalParameters[i]);
        }
        if (big) { this.pw.println(); this.pw.print(AutoIndentWriter.UNINDENT); }
        this.pw.print(')');
        if (fd.thrownExceptions.length > 0) this.pw.print(" throws " + Java.join(fd.thrownExceptions, ", "));
    }

    private void
    unparseDocComment(Java.DocCommentable dc) {
        String optionalDocComment = dc.getDocComment();
        if (optionalDocComment != null) {
            this.pw.print("/**");
            BufferedReader br = new BufferedReader(new StringReader(optionalDocComment));
            for (;;) {
                String line;
                try {
                    line = br.readLine();
                } catch (IOException e) {
                    throw new JaninoRuntimeException(null, e);
                }
                if (line == null) break;
                this.pw.println(line);
                this.pw.print(" *");
            }
            this.pw.println("/");
        }
    }

    private void
    unparseAnnotations(Java.Annotation[] annotations) {
        for (int i = 0; i < annotations.length; i++) {
            annotations[i].accept(this);
        }
    }

    private void
    unparseModifiers(short modifiers) {
        if (modifiers != 0) {
            this.pw.print(Mod.shortToString(modifiers) + ' ');
        }
    }

    private void
    unparseFunctionInvocationArguments(Java.Rvalue[] arguments) {
        boolean big = arguments.length >= 5;
        this.pw.print('(');
        if (big) { this.pw.println(); this.pw.print(AutoIndentWriter.INDENT); }
        for (int i = 0; i < arguments.length; ++i) {
            if (i > 0) {
                if (big) {
                    this.pw.println(',');
                } else
                {
                    this.pw.print(", ");
                }
            }
            this.unparse(arguments[i]);
        }
        if (big) { this.pw.println(); this.pw.print(AutoIndentWriter.UNINDENT); }
        this.pw.print(')');
    }

    @Override public void
    visitMarkerAnnotation(Java.MarkerAnnotation ma) {
        this.pw.append('@').append(ma.type.toString()).append(' ');
    }

    @Override public void
    visitNormalAnnotation(Java.NormalAnnotation na) {
        this.pw.append('@').append(na.type.toString()).append('(');
        for (int i = 0; i < na.elementValuePairs.length; i++) {
            Java.ElementValuePair evp = na.elementValuePairs[i];
            this.pw.append(evp.identifier).append(" = ");
            evp.elementValue.accept(this);
        }
        this.pw.append(") ");
    }

    @Override public void
    visitSingleElementAnnotation(Java.SingleElementAnnotation sea) {
        this.pw.append('@').append(sea.type.toString()).append('(');
        sea.elementValue.accept(this);
        this.pw.append(") ");
    }

    @Override public void
    visitElementValueArrayInitializer(Java.ElementValueArrayInitializer evai) {
        if (evai.elementValues.length == 0) {
            this.pw.append("{}");
            return;
        }

        this.pw.append("{ ");
        evai.elementValues[0].accept(this);
        for (int i = 1; i < evai.elementValues.length; i++) {
            this.pw.append(", ");
            evai.elementValues[i].accept(this);
        }
        this.pw.append(" }");
    }
}
