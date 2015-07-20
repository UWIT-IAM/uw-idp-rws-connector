/*
 * Licensed to the University Corporation for Advanced Internet Development, 
 * Inc. (UCAID) under one or more contributor license agreements.  See the 
 * NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The UCAID licenses this file to You under the Apache 
 * License, Version 2.0 (the "License"); you may not use this file except in 
 * compliance with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.washington.shibboleth.attribute.resolver.dc.rws.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.shibboleth.idp.attribute.IdPAttributeValue;
import net.shibboleth.idp.attribute.resolver.context.AttributeResolutionContext;
import net.shibboleth.utilities.java.support.annotation.constraint.NonnullAfterInit;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.AbstractInitializableComponent;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.primitive.StringSupport;
import net.shibboleth.utilities.java.support.velocity.Template;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.app.event.ReferenceInsertionEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.internet2.middleware.shibboleth.common.attribute.provider.V2SAMLProfileRequestContext;

/**
 * An {@link net.shibboleth.idp.attribute.resolver.dc.impl.ExecutableSearchBuilder} that generates the SQL statement to
 * be executed by evaluating a {@link Template} against the currently resolved attributes within a
 * {@link AttributeResolutionContext}.
 */
public class TemplatedQueryStringBuilder extends AbstractInitializableComponent {

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(TemplatedQueryStringBuilder.class);

    /** Template to be evaluated. */
    private Template template;

    /** Template (as Text) to be evaluated. */
    private String templateText;

    /** VelocityEngine. */
    private VelocityEngine engine;

    /** Event handler used for escaping. */
    // private ReferenceInsertionEventHandler eventHandler = new EscapingReferenceInsertionEventHandler();
    private ReferenceInsertionEventHandler eventHandler = null;

    /** Do we need to make ourself V2 Compatible? */
    private boolean v2Compatibility;

    /**
     * Gets the template to be evaluated.
     * 
     * @return the template
     */
    @NonnullAfterInit public Template getTemplate() {
        return template;
    }

    /**
     * Gets the template text to be evaluated.
     * 
     * @return the template text
     */
    @NonnullAfterInit public String getTemplateText() {
        return templateText;
    }

    /**
     * Sets the template to be evaluated.
     * 
     * @param velocityTemplate template to be evaluated
     */
    public void setTemplateText(@Nullable final String velocityTemplate) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        templateText = StringSupport.trimOrNull(velocityTemplate);
    }

    /**
     * Gets the {@link VelocityEngine} to be used.
     * 
     * @return the template
     */
    @Nullable @NonnullAfterInit public VelocityEngine getVelocityEngine() {
        return engine;
    }

    /**
     * Sets the {@link VelocityEngine} to be used.
     * 
     * @param velocityEngine engine to be used
     */
    public void setVelocityEngine(@Nonnull final VelocityEngine velocityEngine) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        engine = velocityEngine;
    }

    /**
     * Gets the {@link ReferenceInsertionEventHandler} to be used.
     * 
     * @return the reference insertion event handler
     */
    @Nullable public ReferenceInsertionEventHandler getReferenceInsertionEventHandler() {
        return eventHandler;
    }

    /**
     * Sets the {@link ReferenceInsertionEventHandler} to be used.
     * 
     * @param handler reference insertion event handler to be used
     */
    public void setReferenceInsertionEventHandler(@Nullable final ReferenceInsertionEventHandler handler) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        eventHandler = handler;
    }

    /**
     * Are we in V2 Compatibility mode?
     * 
     * @return Returns the v2Compat.
     */
    public boolean isV2Compatibility() {
        return v2Compatibility;
    }

    /**
     * What is out V2 Compatibility mode.
     * 
     * @param compat The mode to set.
     */
    public void setV2Compatibility(boolean compat) {
        v2Compatibility = compat;
    }

    /**
     * Invokes {@link Template#merge(org.apache.velocity.context.Context)} on the supplied context.
     * 
     * @param context to merge
     * 
     * @return result of the merge operation
     */
    protected String merge(@Nonnull final VelocityContext context) {
        final String result = template.merge(context);
        log.debug("Template text {} yields {}", templateText, result);
        return result;
    }

    /**
     * Apply the context to the template. {@inheritDoc}
     */
    protected String getQueryString(@Nonnull final AttributeResolutionContext resolutionContext,
            @Nonnull final Map<String, List<IdPAttributeValue<?>>> dependencyAttributes) {
        final VelocityContext context = new VelocityContext();
        log.trace("Creating query string filter using attribute resolution context {}", resolutionContext);
        context.put("resolutionContext", resolutionContext);

        if (isV2Compatibility()) {
            final V2SAMLProfileRequestContext requestContext = new V2SAMLProfileRequestContext(resolutionContext, null);
            log.trace("Adding v2 request context {}", requestContext);
            context.put("requestContext", requestContext);
        }

        // inject dependencies
        if (dependencyAttributes != null && !dependencyAttributes.isEmpty()) {
            for (final Map.Entry<String, List<IdPAttributeValue<?>>> entry : dependencyAttributes.entrySet()) {
                final List<Object> values = new ArrayList<>(entry.getValue().size());
                for (final IdPAttributeValue<?> value : entry.getValue()) {
                    values.add(value.getValue());
                }
                log.trace("Adding dependency {} to context with {} value(s)", entry.getKey(), values.size());
                context.put(entry.getKey(), values);
            }
        }

        if (eventHandler != null) {
            final EventCartridge cartridge = new EventCartridge();
            cartridge.addEventHandler(eventHandler);
            cartridge.attachToContext(context);
        }

        return merge(context);
    }

    /** {@inheritDoc} */
    @Override protected void doInitialize() throws ComponentInitializationException {
        super.doInitialize();

        if (null == engine) {
            throw new ComponentInitializationException(
                    "TemplatedQueryStringBuilder: no velocity engine was configured");
        }

        if (null == templateText) {
            throw new ComponentInitializationException(
                    "TemplatedQueryStringBuilder: no template text must be non null");
        }

        template = Template.fromTemplate(engine, templateText);
    }

    /** Escapes SQL values added to the template context. */
    protected static class EscapingReferenceInsertionEventHandler implements ReferenceInsertionEventHandler {

        @Override
        public Object referenceInsert(final String reference, final Object value) {
            if (value == null) {
                return null;
            } else if (value instanceof Object[]) {
                final List<Object> encodedValues = new ArrayList<>();
                for (Object o : (Object[]) value) {
                    encodedValues.add(encode(o));
                }
                return encodedValues.toArray();
            } else if (value instanceof Collection<?>) {
                final List<Object> encodedValues = new ArrayList<>();
                for (Object o : (Collection<?>) value) {
                    encodedValues.add(encode(o));
                }
                return encodedValues;
            } else {
                return encode(value);
            }
        }
        
        /**
         * Returns {@link StringEscapeUtils#escapeSql(String)} if value is a string.
         * 
         * @param value to encode
         *
         * @return encoded value if value is a string
         */
        private Object encode(final Object value) {
            if (value instanceof String){ 
                return StringEscapeUtils.escapeSql((String) value);
            }
            return value;
        }
    }
}
