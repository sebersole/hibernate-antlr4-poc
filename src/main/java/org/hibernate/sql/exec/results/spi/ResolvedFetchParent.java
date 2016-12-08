/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.exec.results.spi;

import java.util.List;

import org.hibernate.loader.PropertyPath;
import org.hibernate.sql.ast.select.SqlSelectionDescriptor;
import org.hibernate.sql.convert.results.spi.Fetch;
import org.hibernate.sql.exec.results.process.spi2.InitializerParent;

/**
 * Contract for things that can be the parent of a fetch
 *
 * @author Steve Ebersole
 */
public interface ResolvedFetchParent {
	/**
	 * Get the property path to this parent
	 *
	 * @return The property path
	 */
	PropertyPath getPropertyPath();

	/**
	 * Get the unique-identifier for this fetch parent's table-group.
	 */
	String getTableGroupUniqueIdentifier();

	InitializerParent getInitializerParentForFetchInitializers();

	ResolvedFetch addFetch(List<SqlSelectionDescriptor> sqlSelectionDescriptors, boolean shallow, Fetch queryFetch);
}
