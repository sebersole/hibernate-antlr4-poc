/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.sql.ast;

/**
 * @author Steve Ebersole
 */
public class SelectQuery {
	private final QuerySpec querySpec;

	public SelectQuery(QuerySpec querySpec) {
		this.querySpec = querySpec;
	}

	public QuerySpec getQuerySpec() {
		return querySpec;
	}

}
