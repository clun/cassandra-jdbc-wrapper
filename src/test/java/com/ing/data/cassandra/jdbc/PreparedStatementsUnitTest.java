/*
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.ing.data.cassandra.jdbc;

import com.datastax.driver.core.utils.UUIDs;
import com.datastax.oss.driver.api.core.data.CqlDuration;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.ing.data.cassandra.jdbc.types.DataTypeEnum;
import com.ing.data.cassandra.jdbc.types.JdbcAscii;
import com.ing.data.cassandra.jdbc.types.JdbcBoolean;
import com.ing.data.cassandra.jdbc.types.JdbcInt32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PreparedStatementsUnitTest extends UsingCassandraContainerTest {
    private static final Logger log = LoggerFactory.getLogger(PreparedStatementsUnitTest.class);

    private static final String KEYSPACE = "test_prep_stmt";

    @BeforeAll
    static void finalizeSetUpTests() throws Exception {
        initConnection(KEYSPACE, "version=3.0.0", "localdatacenter=datacenter1");
    }

    @Test
    void givenPreparedStatement_whenGetParameterMetaData_returnExpectedMetadataResultSet() throws SQLException {
        final String cql = "SELECT keyname FROM cf_test_ps WHERE t1bValue = ? AND t1iValue = ? ALLOW FILTERING";
        final CassandraPreparedStatement prepStatement = sqlConnection.prepareStatement(cql);
        prepStatement.setBoolean(1, true);
        prepStatement.setInt(2, 0);
        final ParameterMetaData parameterMetaData = prepStatement.getParameterMetaData();
        assertNotNull(parameterMetaData);
        assertEquals(2, parameterMetaData.getParameterCount());

        // First parameter: boolean value
        assertEquals(ParameterMetaData.parameterModeIn, parameterMetaData.getParameterMode(1));
        assertEquals(Types.BOOLEAN, parameterMetaData.getParameterType(1));
        assertEquals(DataTypeEnum.BOOLEAN.asLowercaseCql(), parameterMetaData.getParameterTypeName(1));
        assertEquals(Boolean.class.getName(), parameterMetaData.getParameterClassName(1));
        assertEquals(JdbcBoolean.INSTANCE.getPrecision(null), parameterMetaData.getPrecision(1));
        assertEquals(JdbcBoolean.INSTANCE.getScale(null), parameterMetaData.getScale(1));

        // Second parameter: integer value
        assertEquals(ParameterMetaData.parameterModeIn, parameterMetaData.getParameterMode(2));
        assertEquals(Types.INTEGER, parameterMetaData.getParameterType(2));
        assertEquals(DataTypeEnum.INT.asLowercaseCql(), parameterMetaData.getParameterTypeName(2));
        assertEquals(Integer.class.getName(), parameterMetaData.getParameterClassName(2));
        assertEquals(JdbcInt32.INSTANCE.getPrecision(null), parameterMetaData.getPrecision(2));
        assertEquals(JdbcInt32.INSTANCE.getScale(null), parameterMetaData.getScale(2));
    }

    @Test
    void givenPreparedStatement_whenGetResultSetMetaData_returnExpectedMetadataResultSet() throws SQLException {
        final String cql = "SELECT keyname AS resKeyname, t1iValue FROM cf_test_ps WHERE t1bValue = ?";
        final CassandraPreparedStatement prepStatement = sqlConnection.prepareStatement(cql);
        prepStatement.setBoolean(1, true);
        prepStatement.execute();
        final ResultSetMetaData rsMetaData = prepStatement.getMetaData();
        assertNotNull(rsMetaData);
        assertEquals(2, rsMetaData.getColumnCount());

        // First column: string value
        assertEquals("reskeyname", rsMetaData.getColumnName(1));
        assertEquals("reskeyname", rsMetaData.getColumnLabel(1));
        assertEquals(String.class.getName(), rsMetaData.getColumnClassName(1));
        assertEquals(DataTypeEnum.TEXT.name(), rsMetaData.getColumnTypeName(1));
        assertEquals(Types.VARCHAR, rsMetaData.getColumnType(1));
        assertEquals(JdbcAscii.INSTANCE.getPrecision(null), rsMetaData.getColumnDisplaySize(1));
        assertEquals(JdbcAscii.INSTANCE.getPrecision(null), rsMetaData.getPrecision(1));
        assertEquals(JdbcAscii.INSTANCE.getScale(null), rsMetaData.getScale(1));
        assertEquals("cf_test_ps", rsMetaData.getTableName(1));
        assertEquals("test_prep_stmt", rsMetaData.getSchemaName(1));
        assertEquals("embedded_test_cluster", rsMetaData.getCatalogName(1));

        // Second column: integer value
        assertEquals("t1ivalue", rsMetaData.getColumnName(2));
        assertEquals("t1ivalue", rsMetaData.getColumnLabel(2));
        assertEquals(Integer.class.getName(), rsMetaData.getColumnClassName(2));
        assertEquals(DataTypeEnum.INT.name(), rsMetaData.getColumnTypeName(2));
        assertEquals(Types.INTEGER, rsMetaData.getColumnType(2));
        assertEquals(JdbcInt32.INSTANCE.getPrecision(null), rsMetaData.getColumnDisplaySize(2));
        assertEquals(JdbcInt32.INSTANCE.getPrecision(null), rsMetaData.getPrecision(2));
        assertEquals(JdbcInt32.INSTANCE.getScale(null), rsMetaData.getScale(2));
        assertEquals("cf_test_ps", rsMetaData.getTableName(2));
        assertEquals("test_prep_stmt", rsMetaData.getSchemaName(2));
        assertEquals("embedded_test_cluster", rsMetaData.getCatalogName(2));
    }

    @Test
    void givenPreparedStatement_whenExecute_insertExpectedValues() throws Exception {
        final Statement statement = sqlConnection.createStatement();
        final String insertQuery = "INSERT INTO test_ps_othertypes "
            + "(col_key, col_tuple, col_inet, col_duration, col_uuid)"
            + "VALUES (?, ?, ?, ?, ?);";
        final PreparedStatement preparedStatement = sqlConnection.prepareStatement(insertQuery);
        preparedStatement.setString(1, "key1");
        final TupleValue aTuple = DataTypes.tupleOf(DataTypes.TEXT, DataTypes.TEXT).newValue("val1", "val2");
        preparedStatement.setObject(2, aTuple, Types.OTHER);
        preparedStatement.setObject(3, InetAddress.getByName("127.0.0.1"), Types.OTHER);
        preparedStatement.setObject(4, CqlDuration.from("15s"), Types.OTHER);
        final UUID generatedUuid = UUIDs.random();
        preparedStatement.setObject(5, generatedUuid, Types.OTHER);
        preparedStatement.execute();

        final ResultSet resultSet = statement.executeQuery("SELECT * FROM test_ps_othertypes WHERE col_key = 'key1';");
        resultSet.next();

        assertEquals(aTuple, resultSet.getObject("col_tuple"));
        assertEquals(InetAddress.getByName("127.0.0.1"), resultSet.getObject("col_inet"));
        assertEquals(CqlDuration.from("15s"), resultSet.getObject("col_duration"));
        assertEquals(generatedUuid, resultSet.getObject("col_uuid"));
    }
}
