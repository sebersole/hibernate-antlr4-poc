/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.convert.results.spi;

import java.util.List;

import org.hibernate.sql.ast.expression.Expression;
import org.hibernate.sql.ast.select.SqlSelectionDescriptor;
import org.hibernate.sql.exec.results.spi.ResolvedReturn;

/**
 * Represents a return value in the query results.
 * <p/>
 * Not the same as a result column in the JDBC ResultSet!  A Return will possibly consume
 * multiple result columns.
 * <p/>
 * Return is distinctly different from a {@link Fetch} and so modeled as completely separate hierarchy.
 *
 * @see ReturnScalar
 * @see ReturnDynamicInstantiation
 * @see EntityReturn
 * @see ReturnCollection
 *
 * @author Steve Ebersole
 */
public interface Return {
	Expression getSelectExpression();

	/**
	 * Get the result variable name associated with this query Return
	 *
	 * @return The result variable name
	 */
	String getResultVariableName();

	ResolvedReturn resolve(List<SqlSelectionDescriptor> sqlSelectionDescriptors, boolean shallow);
}
