/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.convert.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.hibernate.QueryException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.proposed.spi.QueryParameterBindings;
import org.hibernate.sql.ast.sort.SortSpecification;
import org.hibernate.sql.convert.internal.DomainReferenceRendererSelectionImpl;
import org.hibernate.sql.convert.internal.DomainReferenceRendererStandardImpl;
import org.hibernate.sql.spi.ParameterBinder;
import org.hibernate.sql.ast.QuerySpec;
import org.hibernate.sql.ast.SelectQuery;
import org.hibernate.sql.ast.expression.AttributeReference;
import org.hibernate.sql.ast.expression.AvgFunction;
import org.hibernate.sql.ast.expression.BinaryArithmeticExpression;
import org.hibernate.sql.ast.expression.CaseSearchedExpression;
import org.hibernate.sql.ast.expression.CaseSimpleExpression;
import org.hibernate.sql.ast.expression.CoalesceExpression;
import org.hibernate.sql.ast.expression.ColumnBindingExpression;
import org.hibernate.sql.ast.expression.ConcatExpression;
import org.hibernate.sql.ast.expression.CountFunction;
import org.hibernate.sql.ast.expression.CountStarFunction;
import org.hibernate.sql.ast.expression.EntityReference;
import org.hibernate.sql.ast.expression.Expression;
import org.hibernate.sql.ast.expression.MaxFunction;
import org.hibernate.sql.ast.expression.MinFunction;
import org.hibernate.sql.ast.expression.NamedParameter;
import org.hibernate.sql.ast.expression.NonStandardFunctionExpression;
import org.hibernate.sql.ast.expression.NullifExpression;
import org.hibernate.sql.ast.expression.PositionalParameter;
import org.hibernate.sql.ast.expression.QueryLiteral;
import org.hibernate.sql.ast.expression.SumFunction;
import org.hibernate.sql.ast.expression.UnaryOperationExpression;
import org.hibernate.sql.ast.expression.instantiation.DynamicInstantiation;
import org.hibernate.sql.ast.expression.instantiation.DynamicInstantiationArgument;
import org.hibernate.sql.ast.from.ColumnBinding;
import org.hibernate.sql.ast.from.FromClause;
import org.hibernate.sql.ast.from.TableBinding;
import org.hibernate.sql.ast.from.TableGroup;
import org.hibernate.sql.ast.from.TableGroupJoin;
import org.hibernate.sql.ast.from.TableJoin;
import org.hibernate.sql.ast.from.TableSpace;
import org.hibernate.sql.ast.predicate.BetweenPredicate;
import org.hibernate.sql.ast.predicate.FilterPredicate;
import org.hibernate.sql.ast.predicate.GroupedPredicate;
import org.hibernate.sql.ast.predicate.InListPredicate;
import org.hibernate.sql.ast.predicate.InSubQueryPredicate;
import org.hibernate.sql.ast.predicate.Junction;
import org.hibernate.sql.ast.predicate.LikePredicate;
import org.hibernate.sql.ast.predicate.NegatedPredicate;
import org.hibernate.sql.ast.predicate.NullnessPredicate;
import org.hibernate.sql.ast.predicate.Predicate;
import org.hibernate.sql.ast.predicate.RelationalPredicate;
import org.hibernate.sql.ast.select.SelectClause;
import org.hibernate.sql.ast.select.Selection;
import org.hibernate.sql.exec.results.spi.ReturnReader;
import org.hibernate.sqm.query.order.SortOrder;
import org.hibernate.type.LiteralType;
import org.hibernate.type.Type;

import org.jboss.logging.Logger;

/**
 * @author Steve Ebersole
 */
public class SqlTreeWalker implements DomainReferenceRenderer.RenderingContext {
	private static final Logger log = Logger.getLogger( SqlTreeWalker.class );

	// pre-req state
	private final SessionFactoryImplementor sessionFactory;
	private final QueryParameterBindings parameterBindings;
	private final boolean shallow = false; // for now always false, until Query#iterate support finalized

	// In-flight state
	private final StringBuilder sqlBuffer = new StringBuilder();
	private final List<ParameterBinder> parameterBinders = new ArrayList<>();
	private final List<Return> returns = new ArrayList<>();

	// rendering expressions often has to be done differently if it occurs in certain contexts
	private final Stack<DomainReferenceRenderer> domainReferenceRendererStack = new Stack<>( new DomainReferenceRendererStandardImpl( this ) );
	private boolean currentlyInPredicate;
	private boolean currentlyInSelections;

	public SqlTreeWalker(SessionFactoryImplementor sessionFactory, QueryParameterBindings parameterBindings) {
		this.sessionFactory = sessionFactory;
		this.parameterBindings = parameterBindings;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// for now, for tests
	public String getSql() {
		return sqlBuffer.toString();
	}
	public List<ParameterBinder> getParameterBinders() {
		return parameterBinders;
	}
	public List<Return> getReturns() {
		return returns;
	}
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	private void appendSql(String fragment) {
		sqlBuffer.append( fragment );
	}

	public void visitSelectQuery(SelectQuery selectQuery) {
		visitQuerySpec( selectQuery.getQuerySpec() );

	}

	public void visitQuerySpec(QuerySpec querySpec) {
		visitSelectClause( querySpec.getSelectClause() );
		visitFromClause( querySpec.getFromClause() );

		if ( querySpec.getWhereClauseRestrictions() != null && !querySpec.getWhereClauseRestrictions().isEmpty() ) {
			appendSql( " where " );

			boolean wasPreviouslyInPredicate = currentlyInPredicate;
			currentlyInPredicate = true;
			try {
				querySpec.getWhereClauseRestrictions().accept( this );
			}
			finally {
				currentlyInPredicate = wasPreviouslyInPredicate;
			}
		}

		final List<SortSpecification> sortSpecifications = querySpec.getSortSpecifications();
		if ( sortSpecifications != null && !sortSpecifications.isEmpty() ) {
			appendSql( " order by " );

			String separator = "";
			for (SortSpecification sortSpecification : sortSpecifications ) {
				appendSql( separator );
				visitSortSpecification( sortSpecification );
				separator = ", ";
			}
		}

		visitLimitOffsetClause( querySpec );
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// ORDER BY clause

	public void visitSortSpecification(SortSpecification sortSpecification) {
		sortSpecification.getSortExpression().accept( this );

		final String collation = sortSpecification.getCollation();
		if ( collation != null ) {
			appendSql( " collate " );
			appendSql( collation );
		}

		final SortOrder sortOrder = sortSpecification.getSortOrder();
		if ( sortOrder == SortOrder.ASCENDING ) {
			appendSql( " asc" );
		} else if ( sortOrder == SortOrder.DESCENDING ) {
			appendSql( " desc" );
		}
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// LIMIT/OFFSET clause

	public void visitLimitOffsetClause(QuerySpec querySpec) {
		if ( querySpec.getOffsetClauseExpression() != null ) {
			appendSql( " offset " );
			querySpec.getOffsetClauseExpression().accept( this );
			appendSql( " rows" );
		}

		if ( querySpec.getLimitClauseExpression() != null ) {
			appendSql( " fetch first " );
			querySpec.getLimitClauseExpression().accept( this );
			appendSql( " rows only" );
		}
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SELECT clause

	public void visitSelectClause(SelectClause selectClause) {
		currentSelectionProcessor = new SelectionProcessor( currentSelectionProcessor );
		domainReferenceRendererStack.push( new DomainReferenceRendererSelectionImpl( this ) );
		try {
			boolean previouslyInSelections = currentlyInSelections;
			currentlyInSelections = true;

			try {
				appendSql( "select " );
				if ( selectClause.isDistinct() ) {
					appendSql( "distinct " );
				}

				String separator = "";
				for ( Selection selection : selectClause.getSelections() ) {
					appendSql( separator );
					visitSelection( selection );
					separator = ", ";
				}
			}
			finally {
				currentlyInSelections = previouslyInSelections;
			}
		}
		finally {
			currentSelectionProcessor = currentSelectionProcessor.parentSelectionProcessor;
		}
	}

	public void visitSelection(Selection selection) {
		currentSelectionProcessor.processSelection( selection );
		selection.getSelectExpression().accept( this );
	}

	private class SelectionProcessor {
		private final SelectionProcessor parentSelectionProcessor;
		private int numberOfColumnsConsumedSoFar = 0;

		private SelectionProcessor(SelectionProcessor parentSelectionProcessor) {
			this.parentSelectionProcessor = parentSelectionProcessor;
		}

		private void processSelection(Selection selection) {
			if ( parentSelectionProcessor != null ) {
				return;
			}

			// otherwise build a Return
			// 		(atm only simple selection expressions are supported)
			final ReturnReader reader = selection.getSelectExpression().getReturnReader( numberOfColumnsConsumedSoFar+1, shallow, sessionFactory );
			returns.add(
					new Return( selection.getResultVariable(), reader )
			);
			numberOfColumnsConsumedSoFar += reader.getNumberOfColumnsRead( sessionFactory );
		}
	}

	private SelectionProcessor currentSelectionProcessor;


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// FROM clause

	public void visitFromClause(FromClause fromClause) {
		appendSql( " from " );

		String separator = "";
		for ( TableSpace tableSpace : fromClause.getTableSpaces() ) {
			appendSql( separator );
			visitTableSpace( tableSpace );
			separator = ", ";
		}
	}

	public void visitTableSpace(TableSpace tableSpace) {
		visitTableGroup( tableSpace.getRootTableGroup() );

		for ( TableGroupJoin tableGroupJoin : tableSpace.getJoinedTableGroups() ) {
			appendSql( " " );
			appendSql( tableGroupJoin.getJoinType().getText() );
			appendSql( " join " );
			visitTableGroup( tableGroupJoin.getJoinedGroup() );

			boolean wasPreviouslyInPredicate = currentlyInPredicate;
			currentlyInPredicate = true;
			try {
				if ( tableGroupJoin.getPredicate() != null && !tableGroupJoin.getPredicate().isEmpty() ) {
					appendSql( " on " );
					tableGroupJoin.getPredicate().accept( this );
				}
			}
			finally {
				currentlyInPredicate = wasPreviouslyInPredicate;
			}
		}

	}

	public void visitTableGroup(TableGroup tableGroup) {
		visitTableBinding( tableGroup.getRootTableBinding() );

		for ( TableJoin tableJoin : tableGroup.getTableJoins() ) {
			appendSql( " " );
			appendSql( tableJoin.getJoinType().getText() );
			appendSql( " join " );
			visitTableBinding( tableJoin.getJoinedTableBinding() );
			if ( tableJoin.getJoinPredicate() != null && !tableJoin.getJoinPredicate().isEmpty() ) {
				appendSql( " on " );
				tableJoin.getJoinPredicate().accept( this );
			}
		}
	}

	public void visitTableBinding(TableBinding tableBinding) {
		appendSql( tableBinding.getTable().getTableExpression() + " as " + tableBinding.getIdentificationVariable() );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Expressions

	public void visitAttributeReference(AttributeReference attributeReference) {
		// todo : this needs to operate differently in different contexts (mainly for associations)
		//		e.g...
		//			1) In the select clause we should render the complete column bindings for associations
		//			2) In join predicates
		domainReferenceRendererStack.getCurrent().render( attributeReference );
	}

	private void visitColumnBinding(ColumnBinding columnBinding) {
		appendSql( columnBinding.getColumn().render( columnBinding.getIdentificationVariable() ) );
	}

	public void visitAvgFunction(AvgFunction avgFunction) {
		appendSql( "avg(" );
		avgFunction.getArgument().accept( this );
		appendSql( ")" );
	}

	public void visitBinaryArithmeticExpression(BinaryArithmeticExpression arithmeticExpression) {
		arithmeticExpression.getLeftHandOperand().accept( this );
		appendSql( arithmeticExpression.getOperation().getOperatorSqlText() );
		arithmeticExpression.getRightHandOperand().accept( this );
	}

	public void visitCaseSearchedExpression(CaseSearchedExpression caseSearchedExpression) {
		appendSql( "case " );
		for ( CaseSearchedExpression.WhenFragment whenFragment : caseSearchedExpression.getWhenFragments() ) {
			appendSql( " when " );
			whenFragment.getPredicate().accept( this );
			appendSql( " then " );
			whenFragment.getResult().accept( this );
		}
		appendSql( " else " );
		caseSearchedExpression.getOtherwise().accept( this );
		appendSql( " end" );
	}

	public void visitCaseSimpleExpression(CaseSimpleExpression caseSimpleExpression) {
		appendSql( "case " );
		caseSimpleExpression.getFixture().accept( this );
		for ( CaseSimpleExpression.WhenFragment whenFragment : caseSimpleExpression.getWhenFragments() ) {
			appendSql( " when " );
			whenFragment.getCheckValue().accept( this );
			appendSql( " then " );
			whenFragment.getResult().accept( this );
		}
		appendSql( " else " );
		caseSimpleExpression.getOtherwise().accept( this );
		appendSql( " end" );
	}

	public void visitColumnBindingExpression(ColumnBindingExpression columnBindingExpression) {
		visitColumnBinding( columnBindingExpression.getColumnBinding() );
	}

	public void visitCoalesceExpression(CoalesceExpression coalesceExpression) {
		appendSql( "coalesce(" );
		String separator = "";
		for ( Expression expression : coalesceExpression.getValues() ) {
			appendSql( separator );
			expression.accept( this );
			separator = ", ";
		}

		appendSql( ")" );
	}

	public void visitConcatExpression(ConcatExpression concatExpression) {
		appendSql( "concat(" );
		concatExpression.getLeftHandOperand().accept( this );
		appendSql( "," );
		concatExpression.getRightHandOperand().accept( this );
		appendSql( ")" );
	}

	public void visitCountFunction(CountFunction countFunction) {
		appendSql( "count(" );
		if ( countFunction.isDistinct() ) {
			appendSql( "distinct " );
		}
		countFunction.getArgument().accept( this );
		appendSql( ")" );
	}

	public void visitCountStarFunction(CountStarFunction function) {
		appendSql( "count(" );
		if ( function.isDistinct() ) {
			appendSql( "distinct " );
		}
		appendSql( "*)" );
	}

	public void visitDynamicInstantiation(DynamicInstantiation dynamicInstantiation) {
		// this is highly optimistic in thinking that each argument expression renders values to the select, but for now...

		String separator = "";
// note sure why, but the compiler does not like this
//		for ( DynamicInstantiationArgument argument : dynamicInstantiation.getArguments() ) {
//			appendSql( separator );
//			argument.getExpression().accept( this );
//			separator = ", ";
//		}
		for ( Object o : dynamicInstantiation.getArguments() ) {
			appendSql( separator );
			( (DynamicInstantiationArgument) o ).getExpression().accept( this );
			separator = ", ";
		}
	}

	public void visitMaxFunction(MaxFunction maxFunction) {
		appendSql( "max(" );
		if ( maxFunction.isDistinct() ) {
			appendSql( "distinct " );
		}
		maxFunction.getArgument().accept( this );
		appendSql( ")" );
	}

	public void visitMinFunction(MinFunction minFunction) {
		appendSql( "min(" );
		if ( minFunction.isDistinct() ) {
			appendSql( "distinct " );
		}
		minFunction.getArgument().accept( this );
		appendSql( ")" );
	}

	public void visitNamedParameter(NamedParameter namedParameter) {
		parameterBinders.add( namedParameter.getParameterBinder() );

		final Type type = Helper.resolveType( namedParameter, parameterBindings );

		final int columnCount = type.getColumnSpan( sessionFactory );
		final boolean needsParens = currentlyInPredicate && columnCount > 1;

		if ( needsParens ) {
			appendSql( "(" );
		}

		String separator = "";
		for ( int i = 0; i < columnCount; i++ ) {
			appendSql( separator );
			appendSql( "?" );
			separator = ", ";
		}

		if ( needsParens ) {
			appendSql( ")" );
		}
	}

	public void visitNonStandardFunctionExpression(NonStandardFunctionExpression nonStandardFunctionExpression) {
		// todo : look up function registry entry (maybe even when building the SQL tree)
		appendSql( nonStandardFunctionExpression.getFunctionName() );
		if ( !nonStandardFunctionExpression.getArguments().isEmpty() ) {
			appendSql( "(" );
			String separator = "";
			for ( Expression expression : nonStandardFunctionExpression.getArguments() ) {
				appendSql( separator );
				expression.accept( this );
				separator = ", ";
			}
			appendSql( ")" );
		}
	}

	public void visitNullifExpression(NullifExpression nullifExpression) {
		appendSql( "nullif(" );
		nullifExpression.getFirstArgument().accept( this );
		appendSql( ", "  );
		nullifExpression.getSecondArgument().accept( this );
		appendSql( ")" );
	}

	public void visitPositionalParameter(PositionalParameter positionalParameter) {
		parameterBinders.add( positionalParameter.getParameterBinder() );

		final Type type = Helper.resolveType( positionalParameter, parameterBindings );

		final int columnCount = type.getColumnSpan( sessionFactory );
		final boolean needsParens = currentlyInPredicate && columnCount > 1;

		if ( needsParens ) {
			appendSql( "(" );
		}

		String separator = "";
		for ( int i = 0; i < columnCount; i++ ) {
			appendSql( separator );
			appendSql( "?" );
			separator = ", ";
		}

		if ( needsParens ) {
			appendSql( ")" );
		}
	}

	public void visitQueryLiteral(QueryLiteral queryLiteral) {
		if ( !currentlyInSelections ) {
			// handle literals via parameter binding if they occur outside the select
			parameterBinders.add( queryLiteral );

			final int columnCount = queryLiteral.getType().getColumnSpan( sessionFactory );
			final boolean needsParens = currentlyInPredicate && columnCount > 1;

			if ( needsParens ) {
				appendSql( "(" );
			}

			String separator = "";
			for ( int i = 0; i < columnCount; i++ ) {
				appendSql( separator );
				appendSql( "?" );
				separator = ", ";
			}

			if ( needsParens ) {
				appendSql( ")" );
			}
		}
		else {
			// otherwise, render them as literals
			// todo : better scheme for rendering these as literals
			try {
				appendSql(
						( (LiteralType) queryLiteral.getType() ).objectToSQLString( queryLiteral.getValue(), sessionFactory.getDialect() )
				);
			}
			catch (Exception e) {
				throw new QueryException(
						String.format(
								Locale.ROOT,
								"Could not render literal value [%s (%s)] into SQL",
								queryLiteral.getValue(),
								queryLiteral.getType().getName()
						),
						e
				);
			}
		}
	}

	public void visitSumFunction(SumFunction sumFunction) {
		appendSql( "sum(" );
		if ( sumFunction.isDistinct() ) {
			appendSql( "distinct " );
		}
		sumFunction.getArgument().accept( this );
		appendSql( ")" );
	}

	public void visitUnaryOperationExpression(UnaryOperationExpression unaryOperationExpression) {
		if ( unaryOperationExpression.getOperation() == UnaryOperationExpression.Operation.PLUS ) {
			appendSql( "+" );
		}
		else {
			appendSql( "-" );
		}
		unaryOperationExpression.getOperand().accept( this );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Predicates

	public void visitBetweenPredicate(BetweenPredicate betweenPredicate) {
		betweenPredicate.getExpression().accept( this );
		if ( betweenPredicate.isNegated() ) {
			appendSql( " not" );
		}
		appendSql( " between " );
		betweenPredicate.getLowerBound().accept( this );
		appendSql( " and " );
		betweenPredicate.getUpperBound().accept( this );
	}

	public void visitFilterPredicate(FilterPredicate filterPredicate) {
		throw new NotYetImplementedException();
	}

	public void visitGroupedPredicate(GroupedPredicate groupedPredicate) {
		if ( groupedPredicate.isEmpty() ) {
			return;
		}

		appendSql( "(" );
		groupedPredicate.getSubPredicate().accept( this );
		appendSql( ")" );
	}

	public void visitInListPredicate(InListPredicate inListPredicate) {
		inListPredicate.getTestExpression().accept( this );
		if ( inListPredicate.isNegated() ) {
			appendSql( " not" );
		}
		appendSql( " in(" );
		if ( inListPredicate.getListExpressions().isEmpty() ) {
			appendSql( "null" );
		}
		else {
			String separator = "";
			for ( Expression expression : inListPredicate.getListExpressions() ) {
				appendSql( separator );
				expression.accept( this );
				separator = ", ";
			}
		}
		appendSql( ")" );
	}

	public void visitInSubQueryPredicate(InSubQueryPredicate inSubQueryPredicate) {
		inSubQueryPredicate.getTestExpression().accept( this );
		if ( inSubQueryPredicate.isNegated() ) {
			appendSql( " not" );
		}
		appendSql( " in(" );
		visitQuerySpec( inSubQueryPredicate.getSubQuery() );
		appendSql( ")" );
	}

	public void visitJunction(Junction junction) {
		if ( junction.isEmpty() ) {
			return;
		}

		String separator = "";
		for ( Predicate predicate : junction.getPredicates() ) {
			appendSql( separator );
			predicate.accept( this );
			separator = junction.getNature() == Junction.Nature.CONJUNCTION ? " and " : " or ";
		}
	}

	public void visitLikePredicate(LikePredicate likePredicate) {
		likePredicate.getMatchExpression().accept( this );
		if ( likePredicate.isNegated() ) {
			appendSql( " not" );
		}
		appendSql( " like " );
		likePredicate.getPattern().accept( this );
		if ( likePredicate.getEscapeCharacter() != null ) {
			appendSql( " escape " );
			likePredicate.getEscapeCharacter().accept( this );
		}
	}

	public void visitNegatedPredicate(NegatedPredicate negatedPredicate) {
		if ( negatedPredicate.isEmpty() ) {
			return;
		}

		appendSql( "not(" );
		negatedPredicate.getPredicate().accept( this );
		appendSql( ")" );
	}

	public void visitNullnessPredicate(NullnessPredicate nullnessPredicate) {
		nullnessPredicate.getExpression().accept( this );
		if ( nullnessPredicate.isNegated() ) {
			appendSql( " is not null" );
		}
		else {
			appendSql( " is null" );
		}
	}

	public void visitRelationalPredicate(RelationalPredicate relationalPredicate) {
		relationalPredicate.getLeftHandExpression().accept( this );
		appendSql( relationalPredicate.getOperator().sqlText() );
		relationalPredicate.getRightHandExpression().accept( this );
	}

	public void visitEntityExpression(EntityReference entityExpression) {
		domainReferenceRendererStack.getCurrent().render( entityExpression );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// DomainReferenceRenderer.RenderingContext impl

	@Override
	public void renderColumnBindings(ColumnBinding... columnBindings) {
		final boolean needsParens = columnBindings.length > 1 && currentlyInPredicate;
		if ( needsParens ) {
			appendSql( "(" );
		}

		String separator = "";
		for ( ColumnBinding columnBinding : columnBindings ) {
			appendSql( separator );
			visitColumnBinding( columnBinding );
			separator = ", ";
		}

		if ( needsParens ) {
			appendSql( ")" );
		}
	}
}
