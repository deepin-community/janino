
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

import java.io.*;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

import org.codehaus.commons.compiler.*;
import org.codehaus.janino.Java.Type;
import org.codehaus.janino.Visitor.AtomVisitor;
import org.codehaus.janino.Visitor.TypeVisitor;
import org.codehaus.janino.util.*;

/**
 * To set up a {@link SimpleCompiler} object, proceed as described for {@link ISimpleCompiler}.
 * Alternatively, a number of "convenience constructors" exist that execute the described steps
 * instantly.
 */
@SuppressWarnings({ "rawtypes", "unchecked" }) public
class SimpleCompiler extends Cookable implements ISimpleCompiler {
    private static final boolean DEBUG = false;

    private ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();

    // Set when "cook()"ing.
    private ClassLoaderIClassLoader classLoaderIClassLoader;

    private ClassLoader result;

    protected boolean debugSource = Boolean.getBoolean(ICookable.SYSTEM_PROPERTY_SOURCE_DEBUGGING_ENABLE);
    protected boolean debugLines  = this.debugSource;
    protected boolean debugVars   = this.debugSource;

    public static void
    main(String[] args) throws Exception {
        if (args.length >= 1 && "-help".equals(args[0])) {
            System.out.println("Usage:");
            System.out.println("    org.codehaus.janino.SimpleCompiler <source-file> <class-name> { <argument> }");
            System.out.println("Reads a compilation unit from the given <source-file> and invokes method");
            System.out.println("\"public static void main(String[])\" of class <class-name>, passing the.");
            System.out.println("given <argument>s.");
            System.exit(1);
        }

        if (args.length < 2) {
            System.err.println("Source file and/or class name missing; try \"-help\".");
            System.exit(1);
        }

        // Get source file.
        String sourceFileName = args[0];

        // Get class name.
        String className = args[1];

        // Get arguments.
        String[] arguments = new String[args.length - 2];
        System.arraycopy(args, 2, arguments, 0, arguments.length);

        // Compile the source file.
        ClassLoader cl = new SimpleCompiler(sourceFileName, new FileInputStream(sourceFileName)).getClassLoader();

        // Load the class.
        Class c = cl.loadClass(className);

        // Invoke the "public static main(String[])" method.
        Method m = c.getMethod("main", new Class[] { String[].class });
        m.invoke(null, new Object[] { arguments });
    }

    /**
     * Equivalent to<pre>
     * SimpleCompiler sc = new SimpleCompiler();
     * sc.cook(optionalFileName, in);</pre>
     *
     * @see #SimpleCompiler()
     * @see Cookable#cook(String, Reader)
     */
    public
    SimpleCompiler(String optionalFileName, Reader in) throws IOException, CompileException {
        this.cook(optionalFileName, in);
    }

    /**
     * Equivalent to<pre>
     * SimpleCompiler sc = new SimpleCompiler();
     * sc.cook(optionalFileName, is);</pre>
     *
     * @see #SimpleCompiler()
     * @see Cookable#cook(String, InputStream)
     */
    public
    SimpleCompiler(String optionalFileName, InputStream is) throws IOException, CompileException {
        this.cook(optionalFileName, is);
    }

    /**
     * Equivalent to<pre>
     * SimpleCompiler sc = new SimpleCompiler();
     * sc.cook(fileName);</pre>
     *
     * @see #SimpleCompiler()
     * @see Cookable#cookFile(String)
     */
    public
    SimpleCompiler(String fileName) throws IOException, CompileException {
        this.cookFile(fileName);
    }

    /**
     * Equivalent to<pre>
     * SimpleCompiler sc = new SimpleCompiler();
     * sc.setParentClassLoader(optionalParentClassLoader);
     * sc.cook(scanner);</pre>
     *
     * @see #SimpleCompiler()
     * @see #setParentClassLoader(ClassLoader)
     * @see Cookable#cook(Reader)
     */
    public
    SimpleCompiler(Scanner scanner, ClassLoader optionalParentClassLoader) throws IOException, CompileException {
        this.setParentClassLoader(optionalParentClassLoader);
        this.cook(scanner);
    }

    public SimpleCompiler() {}

    @Override public void
    setParentClassLoader(ClassLoader optionalParentClassLoader) {
        assertNotCooked();
        this.parentClassLoader = (
            optionalParentClassLoader != null
            ? optionalParentClassLoader
            : Thread.currentThread().getContextClassLoader()
        );
    }

    @Override public void
    setDebuggingInformation(boolean debugSource, boolean debugLines, boolean debugVars) {
        this.debugSource = debugSource;
        this.debugLines  = debugLines;
        this.debugVars   = debugVars;
    }

    /**
     * Scans, parses and compiles a given compilation unit from the given {@link Reader}. After completion, {@link
     * #getClassLoader()} returns a {@link ClassLoader} that allows for access to the compiled classes.
     */
    @Override public final void
    cook(String optionalFileName, Reader r) throws CompileException, IOException {
        this.cook(new Scanner(optionalFileName, r));
    }

    /**
     * Scans, parses and ompiles a given compilation unit from the given scanner. After completion, {@link
     * #getClassLoader()} returns a {@link ClassLoader} that allows for access to the compiled classes.
     */
    public void
    cook(Scanner scanner) throws CompileException, IOException {
        this.compileToClassLoader(new Parser(scanner).parseCompilationUnit());
    }

    /**
     * Cook this compilation unit directly.
     *  See {@link Cookable#cook}
     */
    public void
    cook(Java.CompilationUnit compilationUnit) throws CompileException {

        // Compile the classes and load them.
        this.compileToClassLoader(compilationUnit);
    }

    @Override public ClassLoader
    getClassLoader() {
        if (this.getClass() != SimpleCompiler.class) {
            throw new IllegalStateException("Must not be called on derived instances");
        }
        if (this.result == null) throw new IllegalStateException("Must only be called after \"cook()\"");
        return this.result;
    }

    /**
     * Two {@link SimpleCompiler}s are regarded equal iff
     * <ul>
     *   <li>Both are objects of the same class (e.g. both are {@link ScriptEvaluator}s)
     *   <li>Both generated functionally equal classes as seen by {@link ByteArrayClassLoader#equals(Object)}
     * </ul>
     */
    @Override public boolean
    equals(Object o) {
        if (!(o instanceof SimpleCompiler)) return false;
        SimpleCompiler that = (SimpleCompiler) o;
        if (this.getClass() != that.getClass()) return false;
        if (this.result == null || that.result == null) {
            throw new IllegalStateException("Equality can only be checked after cooking");
        }
        return this.result.equals(that.result);
    }

    @Override public int
    hashCode() { return this.parentClassLoader.hashCode(); }

    /**
     * Wrap a reflection {@link Class} in a {@link Java.Type} object.
     */
    protected Java.Type
    classToType(final Location location, final Class clazz) {
        if (clazz == null) return null;

        // Can't use a SimpleType here because the classLoaderIClassLoader is not yet set up. Instead, create a
        // Type that lazily creates a delegate Type at COMPILE TIME.
        return new Java.Type(location) {

            private Java.SimpleType delegate;
            
            @Override public String toString()                  { return this.getDelegate().toString(); }
            @Override public void   accept(AtomVisitor visitor) { this.getDelegate().accept((TypeVisitor) visitor); }
            @Override public void   accept(TypeVisitor visitor) { this.getDelegate().accept(visitor); }

            private Type
            getDelegate() {
                if (this.delegate == null) {
                    IClass iClass;
                    try {
                        iClass = SimpleCompiler.this.classLoaderIClassLoader.loadIClass(
                            Descriptor.fromClassName(clazz.getName())
                        );
                    } catch (ClassNotFoundException ex) {
                        throw new JaninoRuntimeException("Loading IClass \"" + clazz.getName() + "\": " + ex);
                    }
                    if (iClass == null) {
                        throw new JaninoRuntimeException(
                            "Cannot load class '"
                            + clazz.getName()
                            + "' through the parent loader"
                        );
                    }
                    if (!clazz.isPrimitive() && ((ReflectionIClass) iClass).getClazz() != clazz) {
                        throw new JaninoRuntimeException(
                            "Class '"
                            + clazz.getName()
                            + "' was loaded through a different loader"
                        );
                    }
                    this.delegate = new Java.SimpleType(location, iClass);
                }

                return this.delegate;
            }
        };
    }

    /**
     * Convert an array of {@link Class}es into an array of{@link Java.Type}s.
     */
    protected Java.Type[]
    classesToTypes(Location location, Class[] classes) {
        Java.Type[] types = new Java.Type[classes.length];
        for (int i = 0; i < classes.length; ++i) {
            types[i] = this.classToType(location, classes[i]);
        }
        return types;
    }

    /**
     * Compile the given compilation unit. (A "compilation unit" is typically the contents
     * of a Java&trade; source file.)
     *
     * @param compilationUnit   The parsed compilation unit
     * @return                  The {@link ClassLoader} into which the compiled classes were defined
     * @throws CompileException
     */
    protected final ClassLoader
    compileToClassLoader(Java.CompilationUnit compilationUnit) throws CompileException {
        if (SimpleCompiler.DEBUG) {
            UnparseVisitor.unparse(compilationUnit, new OutputStreamWriter(System.out));
        }

        this.classLoaderIClassLoader = new ClassLoaderIClassLoader(this.parentClassLoader);

        // Compile compilation unit to class files.
        ClassFile[] classFiles = new UnitCompiler(
            compilationUnit,
            this.classLoaderIClassLoader
        ).compileUnit(this.debugSource, this.debugLines, this.debugVars);

        // Convert the class files to bytes and store them in a Map.
        final Map classes = new HashMap(); // String className => byte[] data
        for (int i = 0; i < classFiles.length; ++i) {
            ClassFile cf       = classFiles[i];
            byte[]    contents = cf.toByteArray();
            if (DEBUG) {
                try {
                    Class disassemblerClass = Class.forName("de.unkrig.jdisasm.Disassembler");
                    disassemblerClass.getMethod(
                        "disasm",
                        new Class[] { InputStream.class }
                    ).invoke(
                        disassemblerClass.newInstance(),
                        new Object[] { new ByteArrayInputStream(contents) }
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            classes.put(cf.getThisClassName(), contents);
        }

        // Create a ClassLoader that loads the generated classes.
        this.result = (ClassLoader) AccessController.doPrivileged(new PrivilegedAction() {

            @Override public Object
            run() {
                return new ByteArrayClassLoader(
                    classes,                              // classes
                    SimpleCompiler.this.parentClassLoader // parent
                );
            }
        });
        return this.result;
    }

    /**
     * Throw an {@link IllegalStateException} if this {@link Cookable} is already cooked.
     */
    protected void
    assertNotCooked() {
        if (this.classLoaderIClassLoader != null) throw new IllegalStateException("Already cooked");
    }
}
