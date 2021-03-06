/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2014 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

/*
 * Use is subject to the license terms.
 */
package com.sun.tools.xjc.generator.bean;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;

import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.generator.annotation.spec.XmlAccessorTypeWriter;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.outline.Aspect;
import com.sun.tools.xjc.outline.Outline;

/**
 * Decides how a bean token is mapped to the generated classes.
 *
 * <p>
 * The actual implementations of this interface is tightly coupled with
 * the backend, but the front-end gets to choose which strategy to be used.
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
@XmlEnum(Boolean.class)
public enum ImplStructureStrategy {
    /**
     * Generates beans only. The simplest code generation.
     */
    @XmlEnumValue("true")
    BEAN_ONLY() {
        protected Result createClasses(Outline outline, CClassInfo bean) {
            JClassContainer parent = outline.getContainer( bean.parent(), Aspect.EXPOSED );

            JDefinedClass impl = outline.getClassFactory().createClass(
                parent,
                JMod.PUBLIC|(parent.isPackage()?0:JMod.STATIC)|(bean.isAbstract()?JMod.ABSTRACT:0),
                bean.shortName, bean.getLocator() );
            impl.annotate2(XmlAccessorTypeWriter.class).value(XmlAccessType.FIELD);

            return new Result(impl,impl);
        }

        protected JPackage getPackage(JPackage pkg, Aspect a) {
            return pkg;
        }

        protected MethodWriter createMethodWriter(final ClassOutlineImpl target) {
            assert target.ref==target.implClass;

            return new MethodWriter(target) {
                private final JDefinedClass impl = target.implClass;

                private JMethod implMethod;

                public JVar addParameter(JType type, String name) {
                    return implMethod.param(type,name);
                }

                public JMethod declareMethod(JType returnType, String methodName) {
                    implMethod = impl.method( JMod.PUBLIC, returnType, methodName );
                    return implMethod;
                }

                public JDocComment javadoc() {
                    return implMethod.javadoc();
                }
            };
        }

        protected void _extends(ClassOutlineImpl derived, ClassOutlineImpl base) {
            derived.implClass._extends(base.implRef);
        }
    },

    /**
     * Generates the interfaces to describe beans (content interfaces)
     * and then the beans themselves in a hidden impl package.
     *
     * Similar to JAXB 1.0.
     */
    @XmlEnumValue("false")
    INTF_AND_IMPL() {
        protected Result createClasses( Outline outline, CClassInfo bean ) {
            JClassContainer parent = outline.getContainer( bean.parent(), Aspect.EXPOSED );

            JDefinedClass intf = outline.getClassFactory().createInterface(
                parent, bean.shortName, bean.getLocator() );

            parent = outline.getContainer(bean.parent(), Aspect.IMPLEMENTATION);
            JDefinedClass impl = outline.getClassFactory().createClass(
                parent,
                JMod.PUBLIC|(parent.isPackage()?0:JMod.STATIC)|(bean.isAbstract()?JMod.ABSTRACT:0),
                bean.shortName+"Impl", bean.getLocator() );
            impl.annotate2(XmlAccessorTypeWriter.class).value(XmlAccessType.FIELD);

            impl._implements(intf);

            return new Result(intf,impl);
        }

        protected JPackage getPackage(JPackage pkg, Aspect a) {
            switch(a) {
            case EXPOSED:
                return pkg;
            case IMPLEMENTATION:
                return pkg.subPackage("impl");
            default:
                assert false;
                throw new IllegalStateException();
            }
        }

        protected MethodWriter createMethodWriter(final ClassOutlineImpl target) {
            return new MethodWriter(target) {
                private final JDefinedClass intf = target.ref;
                private final JDefinedClass impl = target.implClass;

                private JMethod intfMethod;
                private JMethod implMethod;

                public JVar addParameter(JType type, String name) {
                    // TODO: do we still need to deal with the case where intf is null?
                    if(intf!=null)
                        intfMethod.param(type,name);
                    return implMethod.param(type,name);
                }

                public JMethod declareMethod(JType returnType, String methodName) {
                    if(intf!=null)
                        intfMethod = intf.method( 0, returnType, methodName );
                    implMethod = impl.method( JMod.PUBLIC, returnType, methodName );
                    return implMethod;
                }

                public JDocComment javadoc() {
                    if(intf!=null)
                        return intfMethod.javadoc();
                    else
                        return implMethod.javadoc();
                }
            };
        }

        protected void _extends(ClassOutlineImpl derived, ClassOutlineImpl base) {
            derived.implClass._extends(base.implRef);
            derived.ref._implements(base.ref);
        }
    };


    /**
     * Creates class(es) for the given bean.
     */
    protected abstract Result createClasses( Outline outline, CClassInfo bean );

    /**
     * Gets the specified aspect of the given package.
     */
    protected abstract JPackage getPackage( JPackage pkg, Aspect a );

    protected abstract MethodWriter createMethodWriter( ClassOutlineImpl target );

    /**
     * Sets up an inheritance relationship.
     */
    protected abstract void _extends( ClassOutlineImpl derived, ClassOutlineImpl base );

    public static final class Result {
        /**
         * Corresponds to {@link Aspect#EXPOSED}
         */
        public final JDefinedClass exposed;
        /**
         * Corresponds to {@link Aspect#IMPLEMENTATION}
         */
        public final JDefinedClass implementation;

        public Result(JDefinedClass exposed, JDefinedClass implementation) {
            this.exposed = exposed;
            this.implementation = implementation;
        }
    }
}
