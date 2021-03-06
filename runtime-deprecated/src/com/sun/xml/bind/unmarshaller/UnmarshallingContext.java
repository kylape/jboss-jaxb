/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.bind.unmarshaller;

import javax.xml.bind.ValidationEventHandler;
import javax.xml.namespace.NamespaceContext;

import org.relaxng.datatype.ValidationContext;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;

import com.sun.xml.bind.TypeRegistry;

/**
 * Methods exposed by the unmarshalling coordinator object
 * to the generated code.
 * 
 * This interface will be implemented by the coordinator, which
 * converts whatever events (e.g., SAX) into unmarshalling events.
 *
 * <p>
 * Errors detected by the ContentHandlerEx-derived classes should
 * be either thrown as {@link UnrepotedException} or reported through
 * the handleEvent method of this interface.
 *
 * @author
 *  <a href="mailto:kohsuke.kawaguchi@sun.com>Kohsuke KAWAGUCHI</a>
 * @since JAXB1.0
 * @deprecated in JAXB1.0.1
 */
public interface UnmarshallingContext extends
    ValidationContext, ContentHandler, NamespaceContext, ValidationEventHandler
{
    /** Obtains a reference to the current type registry. */
    TypeRegistry getTypeRegistry();
    
    
    
    /**
     * Pushes the current content handler into the stack
     * and registers the newly specified content handler so
     * that it can receive SAX events.
     * 
     * @param memento
     *      When this newly specified handler will be removed from the stack,
     *      the leaveChild event will be fired to the parent handler
     *      with this memento.
     */
    void pushContentHandler( UnmarshallingEventHandler handler, int memento );
    
    /**
     * Pops a content handler from the stack and registers
     * it as the current content handler.
     * 
     * <p>
     * This method will also fire the leaveChild event with the
     * associated memento.
     * 
     * @return
     *      The new content handler. This method DOES NOT return
     *      the previous content handler.
     */
    void popContentHandler() throws UnreportedException;
    
    /**
     * Gets the current handler.
     * 
     * <p>
     * Returns the same object as the getCurrentEventHandler
     * but in a different type.  
     * 
     * @deprecated
     *      Ue the getCurentEventHandler method.
     */
    ContentHandlerEx getCurrentHandler();
    
    /**
     * Gets the current handler.
     */
    UnmarshallingEventHandler getCurrentEventHandler();

// those two methods are defined in SAX ContentHandler
    /**
     * Adds a new namespace declaration.
     * This method should be called by the generated code.
     */
    void startPrefixMapping( String prefix, String namespaceUri );
    
    /**
     * Removes a namespace declaration.
     * This method should be called by the generated code.
     */
    void endPrefixMapping( String prefix );
    
    
    
    /**
     * Stores a new attribute set.
     * This method should be called by the generated code
     * when it "eats" an enterElement event.
     */
    void pushAttributes( Attributes atts );
    
    /**
     * Discards the previously stored attribute set.
     * This method should be called by the generated code
     * when it "eats" a leaveElement event.
     */
    void popAttributes();
    
    /**
     * Gets the index of the attribute with the specified name.
     * This is usually faster when you only need to test with
     * a simple name.
     * 
     * @return
     *      -1 if not found.
     */
    int getAttribute( String uri, String name );
    
    /**
     * Gets all the unconsumed attributes.
     * If you need to find attributes based on more complex filter,
     * you need to use this method.
     */
    Attributes getUnconsumedAttributes();
    
    /**
     * Fires an attribute event for the specified attribute.
     */
    void consumeAttribute( int idx ) throws UnreportedException;
    
    
    /**
     * Adds a job that will be executed at the last of the unmarshalling.
     * This method is used to support ID/IDREF feature, but it can be used
     * for other purposes as well.
     * 
     * @param   job
     *      The run method of this object is called.
     */
    void addPatcher( Runnable job );
    // TODO: shall we use a modified version of Runnable so that
    // the patcher can throw JAXBException?
    
    /**
     * Adds the object which is currently being unmarshalled
     * to the ID table.
     * 
     * @return
     *      Returns the value passed as the parameter.
     *      This is a hack, but this makes it easier for ID
     *      transducer to do its job.
     */
    String addToIdTable( String id );
    // TODO: what shall we do if the ID is already declared?
    //
    // throwing an exception is one way. Overwriting the previous one
    // is another way. The latter allows us to process invalid documents,
    // while the former makes it impossible to handle them.
    //
    // I prefer to be flexible in terms of invalid document handling,
    // so chose not to throw an exception.
    //
    // I believe this is an implementation choice, not the spec issue.
    // -kk
    
    /**
     * Looks up the ID table and gets associated object.
     * 
     * @return
     *      If there is no object associated with the given id,
     *      this method returns null.
     */
    UnmarshallableObject getObjectFromId( String id );
    // TODO: maybe we should throw UnmarshallingException
    // if we don't find ID.
    
    
    /**
     * Gets the current source location information.
     */
    Locator getLocator();
    
    
// DBG
    /**
     * Gets a tracer object.
     * 
     * Tracer can be used to trace the unmarshalling behavior.
     * Note that to debug the unmarshalling process,
     * you have to configure XJC so that it will emit trace codes
     * in the unmarshaller.
     */
    Tracer getTracer();
}
