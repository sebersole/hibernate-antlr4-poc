/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.sql.orm.internal;

import org.hibernate.sql.orm.QueryParameter;
import org.hibernate.sql.orm.QueryParameterBinding;
import org.hibernate.type.Type;

/**
 * @author Steve Ebersole
 */
public class QueryParameterBindingImpl implements QueryParameterBinding {
	private final QueryParameter parameter;

	private Type bindType;
	private Object bindValue;

	public QueryParameterBindingImpl(QueryParameter parameter) {
		this.parameter = parameter;
		this.bindType = parameter.getExpectedType();
	}

	@Override
	public QueryParameter getParameter() {
		return parameter;
	}

	@Override
	public Object getBindValue() {
		return bindValue;
	}

	@Override
	public Type getBindType() {
		return bindType;
	}

	@Override
	public void setBindValue(Object value) {
		if ( value == null ) {
			throw new IllegalArgumentException( "Cannot bind null to query parameter" );
		}
		this.bindValue = value;
	}

	@Override
	public void setBindValue(Object value, Type clarifiedType) {
		setBindValue( value );
		this.bindType = clarifiedType;
	}
}
