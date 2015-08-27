package org.hibernate.sql.gen;

import java.sql.PreparedStatement;

import org.hibernate.sql.orm.QueryOptions;

/**
 * Performs parameter value binding to a JDBC PreparedStatement.
 *
 * @author Steve Ebersole
 * @author johara
 */
public interface ParameterBinder {
	void bindParameterValue(PreparedStatement statement, QueryOptions queryOptions);
}
