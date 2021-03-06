/**
 * Copyright (C) 2012 Ness Computing, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nesscomputing.mongo;

import static java.lang.String.format;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import com.nesscomputing.config.ConfigProvider;
import com.nesscomputing.lifecycle.LifecycleStage;
import com.nesscomputing.lifecycle.guice.AbstractLifecycleProvider;
import com.nesscomputing.lifecycle.guice.LifecycleAction;

import org.apache.commons.lang3.StringUtils;
import org.weakref.jmx.guice.MBeanModule;
/**
 * Defines a new Mongo writer. Multiple modules can be installed for writing to multiple collections.
 */
public class MongoWriterModule extends AbstractModule
{
    private final String writerName;

    public MongoWriterModule(final String writerName)
    {
        Preconditions.checkState(!StringUtils.isBlank(writerName), "Writer name must not be blank!");

        this.writerName = writerName;
    }

    @Override
    protected void configure()
    {
        final Named named = Names.named(writerName);
        bind(MongoWriterConfig.class).annotatedWith(named).toProvider(ConfigProvider.of(MongoWriterConfig.class, ImmutableMap.of("writername", writerName))).in(Scopes.SINGLETON);
        bind(MongoWriter.class).annotatedWith(named).toProvider(new MongoWriterProvider(named)).asEagerSingleton();

        install(new MBeanModule() {
            @Override
            public void configureMBeans() {
                export(MongoWriter.class).annotatedWith(named).as(format("ness.mongo.writer:name=%s", writerName));
            }
        });
    }

    public static class MongoWriterProvider extends AbstractLifecycleProvider<MongoWriter> implements Provider<MongoWriter>
    {
        private final Named named;
        private MongoWriterConfig writerConfig = null;

        private MongoWriterProvider(final Named named)
        {
            this.named = named;

            addAction(LifecycleStage.START_STAGE, new LifecycleAction<MongoWriter>() {
                    @Override
                    public void performAction(final MongoWriter mongoWriter) {
                        mongoWriter.start();
                    }
                });

            addAction(LifecycleStage.STOP_STAGE, new LifecycleAction<MongoWriter>() {
                    @Override
                    public void performAction(final MongoWriter mongoWriter) {
                        mongoWriter.stop();
                    }
                });
        }

        @Inject
        void setInjector(final Injector injector)
        {
            this.writerConfig = injector.getInstance(Key.get(MongoWriterConfig.class, named));
        }

        @Override
        public MongoWriter internalGet()
        {
            Preconditions.checkState(writerConfig != null, "no writerConfig was injected!");
            return new MongoWriter(writerConfig);
        }
    }
}
