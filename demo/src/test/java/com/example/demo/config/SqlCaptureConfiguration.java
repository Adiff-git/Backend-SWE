package com.example.demo.config;
@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
public class SqlCaptureConfiguration {
        @org.springframework.context.annotation.Bean
        static org.springframework.beans.factory.config.BeanPostProcessor countJdbcExecutions() {
            return new org.springframework.beans.factory.config.BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof javax.sql.DataSource source) {
                        return new org.springframework.jdbc.datasource.DelegatingDataSource(source) {
                            @Override
                            public java.sql.Connection getConnection() throws java.sql.SQLException {
                                return SqlCapture.connection(source.getConnection());
                            }
                            @Override
                            public java.sql.Connection getConnection(String user, String password) throws java.sql.SQLException {
                                return SqlCapture.connection(source.getConnection(user, password));
                            }
                        };
                    }
                    return bean;
                }
            };
        }

}
