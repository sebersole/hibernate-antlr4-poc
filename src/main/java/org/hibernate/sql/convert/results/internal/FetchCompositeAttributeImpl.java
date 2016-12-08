/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.convert.results.internal;

import java.util.List;

import org.hibernate.engine.FetchStrategy;
import org.hibernate.loader.PropertyPath;
import org.hibernate.persister.common.spi.AttributeDescriptor;
import org.hibernate.persister.common.spi.SingularAttributeDescriptor;
import org.hibernate.sql.ast.select.SqlSelectionDescriptor;
import org.hibernate.sql.convert.results.spi.FetchCompositeAttribute;
import org.hibernate.sql.convert.results.spi.FetchParent;
import org.hibernate.sql.exec.results.internal.ResolvedFetchCompositeImpl;
import org.hibernate.sql.exec.results.spi.ResolvedFetchComposite;
import org.hibernate.sql.exec.results.spi.ResolvedFetchParent;
import org.hibernate.type.CompositeType;
import org.hibernate.type.Type;

/**
 * @author Steve Ebersole
 */
public class FetchCompositeAttributeImpl extends AbstractFetchParent implements FetchCompositeAttribute {
	private final FetchParent fetchParent;
	private final SingularAttributeDescriptor fetchedAttribute;
	private final FetchStrategy fetchStrategy;

	public FetchCompositeAttributeImpl(
			FetchParent fetchParent,
			SingularAttributeDescriptor fetchedAttribute,
			FetchStrategy fetchStrategy) {
		super(
				fetchParent.getPropertyPath().append( fetchedAttribute.getAttributeName() ),
				fetchParent.getTableGroupUniqueIdentifier()
		);
		this.fetchParent = fetchParent;
		this.fetchedAttribute = fetchedAttribute;
		this.fetchStrategy = fetchStrategy;
	}

	@Override
	public FetchParent getFetchParent() {
		return fetchParent;
	}

	@Override
	public AttributeDescriptor getFetchedAttributeDescriptor() {
		return fetchedAttribute;
	}

	@Override
	public FetchStrategy getFetchStrategy() {
		return fetchStrategy;
	}

	@Override
	public CompositeType getFetchedType() {
		return (CompositeType) fetchedAttribute.getOrmType();
	}

	@Override
	public boolean isNullable() {
		return fetchedAttribute.isNullable();
	}

	@Override
	public ResolvedFetchComposite resolve(
			ResolvedFetchParent resolvedFetchParent,
			List<SqlSelectionDescriptor> sqlSelectionDescriptors,
			boolean shallow) {
		return new ResolvedFetchCompositeImpl(
				resolvedFetchParent,
				getPropertyPath(),
				getFetchedType(),
				getTableGroupUniqueIdentifier()
		);
	}
}
