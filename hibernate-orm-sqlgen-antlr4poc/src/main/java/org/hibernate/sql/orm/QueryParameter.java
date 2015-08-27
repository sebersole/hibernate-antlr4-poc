/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.sql.orm;

import org.hibernate.type.Type;

/**
 * Represents a parameter defined in the source (HQL/JPQL or criteria) query.
 *
 * @author Steve Ebersole
 */
public interface QueryParameter {
	Type getExpectedType();
}
