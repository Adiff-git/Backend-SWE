package com.example.demo.config;

    /** Test-only JDBC interception,calibrated against prepared and ordinary Statement execution. */
    public final class SqlCapture {
        private static final ThreadLocal<java.util.List<String>> ACTIVE = new ThreadLocal<>();
        public static void begin() { ACTIVE.set(new java.util.ArrayList<>()); }
        public static java.util.List<String> end() {
            var sql = java.util.List.copyOf(ACTIVE.get());
            ACTIVE.remove();
            return sql;
        }
        public static java.sql.Connection connection(java.sql.Connection delegate) {
            return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                    SqlCapture.class.getClassLoader(), new Class<?>[]{java.sql.Connection.class}, (proxy, method, args) -> {
                        Object result = invoke(delegate, method, args);
                        if (result instanceof java.sql.Statement statement) {
                            String sql = args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                            Class<?> contract = result instanceof java.sql.CallableStatement ? java.sql.CallableStatement.class
                                    : result instanceof java.sql.PreparedStatement ? java.sql.PreparedStatement.class : java.sql.Statement.class;
                            return java.lang.reflect.Proxy.newProxyInstance(SqlCapture.class.getClassLoader(),
                                    new Class<?>[]{contract}, (unused, operation, parameters) -> {
                                        if (operation.getName().startsWith("execute") && ACTIVE.get() != null) {
                                            String executed = parameters != null && parameters.length > 0
                                                    && parameters[0] instanceof String text ? text : sql;
                                            ACTIVE.get().add(executed == null ? operation.getName() : executed);
                                        }
                                        return invoke(statement, operation, parameters);
                                    });
                        }
                        return result;
                    });
        }
        private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (java.lang.reflect.InvocationTargetException exception) { throw exception.getCause(); }
        }
    }

