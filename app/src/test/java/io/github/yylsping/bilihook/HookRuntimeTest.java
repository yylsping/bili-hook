package io.github.yylsping.bilihook;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import io.github.libxposed.api.XposedInterface;

public final class HookRuntimeTest {
    private static final Method INTERCEPT;
    private static final Executable TARGET;

    static {
        try {
            INTERCEPT = HookRuntime.class.getDeclaredMethod(
                    "intercept", XposedInterface.Chain.class, HookRuntime.Callback.class);
            INTERCEPT.setAccessible(true);
            TARGET = HookRuntimeTest.class.getDeclaredMethod("target", Object.class, Object.class);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    @SuppressWarnings("unused")
    private static Object target(Object first, Object second) {
        return null;
    }

    @Test
    public void readOnlyCallbackUsesOriginalArgumentPath() throws Throwable {
        FakeChain chain = new FakeChain("receiver", new Object[]{"first", "second"}, "result");

        Object result = intercept(chain, new HookRuntime.Callback() {
            @Override
            protected void beforeHookedMethod(HookRuntime.HookParam param) {
                assertEquals("first", param.getArg(0));
            }

            @Override
            protected void afterHookedMethod(HookRuntime.HookParam param) {
                assertEquals("result", param.getResult());
            }
        });

        assertEquals("result", result);
        assertEquals(0, chain.getArgsCalls);
        assertEquals(1, chain.proceedCalls);
        assertEquals(0, chain.proceedWithArgsCalls);
    }

    @Test
    public void firstMutationCopiesArgumentsOnce() throws Throwable {
        FakeChain chain = new FakeChain("receiver", new Object[]{"first", "second"}, "result");

        Object result = intercept(chain, new HookRuntime.Callback() {
            @Override
            protected void beforeHookedMethod(HookRuntime.HookParam param) {
                param.setArg(1, "changed");
                param.setArg(0, "also changed");
                assertEquals("changed", param.getArg(1));
            }
        });

        assertEquals("result", result);
        assertEquals(1, chain.getArgsCalls);
        assertEquals(0, chain.proceedCalls);
        assertEquals(1, chain.proceedWithArgsCalls);
        assertArrayEquals(new Object[]{"also changed", "changed"}, chain.proceededArgs);
    }

    @Test
    public void earlyResultSkipsOriginalAndStillRunsAfterCallback() throws Throwable {
        FakeChain chain = new FakeChain("receiver", new Object[0], "original");
        final int[] afterCalls = {0};

        Object result = intercept(chain, new HookRuntime.Callback() {
            @Override
            protected void beforeHookedMethod(HookRuntime.HookParam param) {
                param.setResult("replacement");
            }

            @Override
            protected void afterHookedMethod(HookRuntime.HookParam param) {
                afterCalls[0]++;
            }
        });

        assertEquals("replacement", result);
        assertEquals(1, afterCalls[0]);
        assertEquals(0, chain.proceedCalls + chain.proceedWithArgsCalls);
        assertEquals(0, chain.getArgsCalls);
    }

    @Test
    public void originalThrowableIsTransmitted() throws Throwable {
        RuntimeException failure = new RuntimeException("original failure");
        FakeChain chain = new FakeChain("receiver", new Object[0], null);
        chain.failure = failure;

        try {
            intercept(chain, new HookRuntime.Callback() {});
        } catch (Throwable caught) {
            assertSame(failure, caught);
            return;
        }
        throw new AssertionError("original throwable was swallowed");
    }

    @Test
    public void failedNativeDownloadStillClosesVipScopeAndPreservesException() throws Throwable {
        DownloadQualityScope scope = new DownloadQualityScope();
        RuntimeException failure = new RuntimeException("download failed");
        FakeChain chain = new FakeChain("pane", new Object[0], null);
        chain.failure = failure;
        try {
            intercept(chain, new HookRuntime.Callback() {
                @Override protected void beforeHookedMethod(HookRuntime.HookParam p) {
                    scope.enter(true, 120, false);
                }
                @Override protected void afterHookedMethod(HookRuntime.HookParam p) {
                    assertTrue(scope.isActive());
                    scope.exit();
                }
            });
        } catch (Throwable caught) {
            assertSame(failure, caught);
            assertFalse(scope.isActive());
            return;
        }
        throw new AssertionError("native failure was swallowed");
    }

    @Test
    public void afterCallbackCanReplaceOriginalThrowable() throws Throwable {
        FakeChain chain = new FakeChain("receiver", new Object[0], null);
        chain.failure = new RuntimeException("original failure");

        Object result = intercept(chain, new HookRuntime.Callback() {
            @Override
            protected void afterHookedMethod(HookRuntime.HookParam param) {
                param.setResult("recovered");
            }
        });

        assertEquals("recovered", result);
    }

    @Test
    public void beforeCallbackFailureFallsBackToUnmodifiedCall() throws Throwable {
        FakeChain chain = new FakeChain("receiver", new Object[]{"first", "second"}, "original");

        Object result = intercept(chain, new HookRuntime.Callback() {
            @Override
            protected void beforeHookedMethod(HookRuntime.HookParam param) {
                param.setArg(0, "changed");
                throw new RuntimeException("callback failure");
            }
        });

        assertEquals("original", result);
        assertEquals(1, chain.getArgsCalls);
        assertEquals(1, chain.proceedCalls);
        assertEquals(0, chain.proceedWithArgsCalls);
    }

    private static Object intercept(FakeChain chain, HookRuntime.Callback callback) throws Throwable {
        try {
            return INTERCEPT.invoke(null, chain, callback);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private static final class FakeChain implements XposedInterface.Chain {
        private final Object thisObject;
        private final Object[] args;
        private final Object result;
        private Throwable failure;
        private int getArgsCalls;
        private int proceedCalls;
        private int proceedWithArgsCalls;
        private Object[] proceededArgs;

        FakeChain(Object thisObject, Object[] args, Object result) {
            this.thisObject = thisObject;
            this.args = args;
            this.result = result;
        }

        @Override
        public Executable getExecutable() {
            return TARGET;
        }

        @Override
        public Object getThisObject() {
            return thisObject;
        }

        @Override
        public List<Object> getArgs() {
            getArgsCalls++;
            return Arrays.asList(args);
        }

        @Override
        public Object getArg(int index) {
            return args[index];
        }

        @Override
        public Object proceed() throws Throwable {
            proceedCalls++;
            if (failure != null) throw failure;
            return result;
        }

        @Override
        public Object proceed(Object[] replacementArgs) throws Throwable {
            proceedWithArgsCalls++;
            proceededArgs = replacementArgs.clone();
            if (failure != null) throw failure;
            return result;
        }

        @Override
        public Object proceedWith(Object replacementThisObject) throws Throwable {
            return proceed();
        }

        @Override
        public Object proceedWith(Object replacementThisObject, Object[] replacementArgs)
                throws Throwable {
            return proceed(replacementArgs);
        }
    }
}
