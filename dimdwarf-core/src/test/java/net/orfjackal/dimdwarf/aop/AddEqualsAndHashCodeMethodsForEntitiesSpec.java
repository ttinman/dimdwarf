// Copyright © 2008-2010 Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://dimdwarf.sourceforge.net/LICENSE

package net.orfjackal.dimdwarf.aop;

import jdave.*;
import jdave.junit4.JDaveRunner;
import net.orfjackal.dimdwarf.aop.conf.*;
import net.orfjackal.dimdwarf.api.*;
import net.orfjackal.dimdwarf.api.internal.*;
import net.orfjackal.dimdwarf.context.*;
import net.orfjackal.dimdwarf.entities.*;
import org.junit.runner.RunWith;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.util.CheckClassAdapter;

import java.lang.instrument.ClassFileTransformer;

@RunWith(JDaveRunner.class)
@Group({"fast"})
public class AddEqualsAndHashCodeMethodsForEntitiesSpec extends Specification<Object> {

    private static final EntityId ID1 = new EntityObjectId(1);

    private int entityHelperCalled = 0;
    private Object target;

    public void create() throws Exception {
        // the EntityHelper class will call EntityReferenceFactory on every equals() and hashCode() operation
        EntityReferenceFactory factory = new EntityReferenceFactory() {
            public <T> EntityReference<T> createReference(T entity) {
                entityHelperCalled++;
                return new EntityReferenceImpl<T>(ID1, entity);
            }
        };
        ThreadContext.setUp(new FakeContext().with(EntityReferenceFactory.class, factory));
    }

    public void destroy() throws Exception {
        ThreadContext.tearDown();
    }

    private static Object newInstrumentedInstance(Class<?> cls) throws Exception {
        return instrumentClass(cls).newInstance();
    }

    private static Class<?> instrumentClass(Class<?> cls) throws ClassNotFoundException {
        final AopApi api = new DimdwarfAopApi();
        ClassFileTransformer transformer = new AbstractTransformationChain() {
            protected ClassVisitor getAdapters(ClassVisitor cv) {
                cv = new CheckClassAdapter(cv);
                cv = new AddEqualsAndHashCodeMethodsForEntities(api, cv);
                cv = new AddMarkerInterfaceForEntities(api, cv);
                return cv;
            }
        };
        ClassLoader loader = new TransformationTestClassLoader(cls.getPackage().getName() + ".*", transformer);
        return loader.loadClass(cls.getName());
    }


    public class ANormalObject {

        public void create() throws Exception {
            target = newInstrumentedInstance(DummyObject.class);
        }

        public void doesNotDelegateItsEqualsMethod() {
            target.equals(new Object());
            specify(entityHelperCalled, should.equal(0));
        }

        public void doesNotDelegateItsHashCodeMethod() {
            target.hashCode();
            specify(entityHelperCalled, should.equal(0));
        }
    }

    public class AnEntityWithNoEqualsAndHashCodeMethods {

        public void create() throws Exception {
            target = newInstrumentedInstance(DummyEntity.class);
        }

        public void delegatesItsEqualsMethodToEntityHelper() {
            target.equals(new Object());
            specify(entityHelperCalled, should.equal(1));
        }

        public void delegatesItsHashCodeMethodToEntityHelper() {
            target.hashCode();
            specify(entityHelperCalled, should.equal(1));
        }
    }

    public class AnEntityWithACustomEqualsMethod {

        public void create() throws Exception {
            target = newInstrumentedInstance(EntityWithEquals.class);
        }

        public void doesNotDelegateItsEqualsMethod() {
            target.equals(new Object());
            specify(entityHelperCalled, should.equal(0));
        }

        public void delegatesItsHashCodeMethodToEntityHelper() {
            target.hashCode();
            specify(entityHelperCalled, should.equal(1));
        }
    }

    public class AnEntityWithACustomHashCodeMethod {

        public void create() throws Exception {
            target = newInstrumentedInstance(EntityWithHashCode.class);
        }

        public void delegatesItsEqualsMethodToEntityHelper() {
            target.equals(new Object());
            specify(entityHelperCalled, should.equal(1));
        }

        public void doesNotDelegateItsHashCodeMethod() {
            target.hashCode();
            specify(entityHelperCalled, should.equal(0));
        }
    }

    public class ASubclassOfAnAlreadyInstrumentedEntity {

        public void create() throws Exception {
            target = newInstrumentedInstance(SubclassOfEntityWithHashCode.class);
        }

        public void isNotInstrumentedASecondTime() {
            // If the subclasses would be instrumented, then it could override a custom
            // equals/hashCode method in the superclass. 
            target.hashCode();
            specify(entityHelperCalled, should.equal(0));
        }
    }

    public class AnInterfaceWhichIsAccidentallyMarkedAsAnEntity {

        public void shouldNotBeInstrumented() throws Exception {
            // If methods are added to an interface, the class loader will throw a ClassFormatError
            specify(instrumentClass(InterfaceMarkedAsEntity.class), should.not().equal(null));
        }
    }


    @Entity
    public static class EntityWithEquals {
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    @Entity
    public static class EntityWithHashCode {
        public int hashCode() {
            return super.hashCode();
        }
    }

    public static class SubclassOfEntityWithHashCode extends EntityWithHashCode {
    }

    @Entity
    public static interface InterfaceMarkedAsEntity {
    }
}

// TODO: When calling the equals/hashCode method of a transparent reference proxy whose target class has
// custom equals/hashCode methods, the method call should be delegated to the actual entity and not the proxy.
// Maybe it would be wise to annotate the generated methods with javax.annotation.Generated, so that
// net.orfjackal.dimdwarf.entities.tref.TransparentReferenceFactoryImpl.TransparentReferenceCallbackFilter
// can check whether it should delegate those methods.
