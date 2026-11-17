/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.DefaultEncodedValueFactory;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.RouteNetwork;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.util.Helper;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;

import java.io.StringReader;
import java.util.*;

import static com.graphhopper.routing.weighting.custom.CustomModelParser.IN_AREA_PREFIX;

/**
 * Checks an if/else_if condition against an allow list and builds the Java code for it from the parsed AST. The code is
 * never built by copying parts of the user text: Janino's column numbers restart on every line and do not match the
 * input after tabs or unicode escapes, so text replacements based on them could move user text out of a string
 * literal and into the compiled code (GHSA-mg8w-mq9r-f9c6, reimplements the upstream fix 4c0e5bda8).
 */
// ORS-GH MOD - class rewritten against GHSA-mg8w-mq9r-f9c6, see the javadoc above. The invariant to keep when
// merging upstream code into this file: the converted condition is generated from the parsed expression and must
// never again be assembled by copying the user text at Janino column numbers.
class ExpressionVisitor {

    // ORS-GH MOD - operators are written as fixed strings, never copied from the user text
    private static final Set<String> allowedBinaryOps = new HashSet<>(Arrays.asList(
            "==", "!=", "<", ">", "<=", ">=", "&&", "||", "*", "+", "-", "/", "%"));
    private static final String HELPER = CustomWeightingHelper.class.getSimpleName();

    private final ParseResult result;
    private final EncodedValueLookup lookup;
    private final NameValidator nameValidator;
    private final Set<String> allowedMethods = new HashSet<>(Arrays.asList("ordinal", "getDistance", "getName",
            "contains", "sqrt", "abs"));

    public ExpressionVisitor(ParseResult result, NameValidator nameValidator, EncodedValueLookup lookup) {
        this.result = result;
        this.nameValidator = nameValidator;
        this.lookup = lookup;
    }

    // allow only methods and other identifiers (constants and encoded values)
    boolean isValidIdentifier(String identifier) {
        if (nameValidator.isValid(identifier)) {
            if (!Character.isUpperCase(identifier.charAt(0)))
                result.guessedVariables.add(identifier);
            return true;
        }
        return false;
    }

    // ORS-GH MOD START - security fix GHSA-mg8w-mq9r-f9c6: these methods replace the visitRvalue/Replacement pair
    // that validated the condition and then patched the original text by position. Every branch returns the code it
    // allows and throws otherwise, so nothing unchecked can reach the compiler.
    /**
     * Checks rv against the allow list and returns the Java code for it. Throws for anything not allowed, so nothing
     * unexpected can reach the compiler.
     */
    String emit(Java.Rvalue rv) {
        if (rv instanceof Java.AmbiguousName) {
            return emitName((Java.AmbiguousName) rv);
        } else if (rv instanceof Java.Literal) {
            // the token as scanned: a string or char literal is closed and its inner quotes are escaped
            return ((Java.Literal) rv).value;
        } else if (rv instanceof Java.UnaryOperation) {
            Java.UnaryOperation uo = (Java.UnaryOperation) rv;
            if (!uo.operator.equals("!") && !uo.operator.equals("-"))
                throw new IllegalArgumentException("unary operator " + uo.operator + " not allowed");
            String operand = emit(uo.operand);
            // "- -x" must not become the decrement "--x"
            return uo.operator.equals("-") && operand.startsWith("-") ? "- " + operand : uo.operator + operand;
        } else if (rv instanceof Java.MethodInvocation) {
            return emitMethod((Java.MethodInvocation) rv);
        } else if (rv instanceof Java.ParenthesizedExpression) {
            return "(" + emit(((Java.ParenthesizedExpression) rv).value) + ")";
        } else if (rv instanceof Java.BinaryOperation) {
            return emitBinaryOperation((Java.BinaryOperation) rv);
        }
        throw new IllegalArgumentException("invalid expression " + rv);
    }

    private String emitName(Java.AmbiguousName n) {
        if (n.identifiers.length != 1)
            throw new IllegalArgumentException("identifier " + n + " invalid");
        String arg = n.identifiers[0];
        if (arg.startsWith(IN_AREA_PREFIX)) {
            result.guessedVariables.add(arg);
            return HELPER + ".in(this." + arg + ", edge)";
        }
        // e.g. like road_class
        if (isValidIdentifier(arg))
            return arg;
        boolean knownEncodedValue;
        try {
            new DefaultEncodedValueFactory().create(arg);
            knownEncodedValue = true;
        } catch (Exception _) {
            knownEncodedValue = false;
        }
        throw new IllegalArgumentException(knownEncodedValue
                ? "encoded value '" + arg + "' not available"
                : "identifier " + n + " invalid");
    }

    private String emitMethod(Java.MethodInvocation mi) {
        String illegal = mi.methodName + " is illegal method";
        // skip methods like this.in() and chained calls like edge.getName().contains("A")
        if (!allowedMethods.contains(mi.methodName) || mi.target == null
                || !(mi.target.toRvalue() instanceof Java.AmbiguousName))
            throw new IllegalArgumentException(illegal);
        // Janino models the target of "a.b()" as an AmbiguousName with the identifiers [a, b]
        Java.AmbiguousName n = (Java.AmbiguousName) mi.target.toRvalue();
        if (n.identifiers.length != 2 || mi.arguments.length > 1)
            throw new IllegalArgumentException(illegal);
        // edge.getDistance, Math.sqrt => check target name (edge or Math)
        String parent = n.identifiers[0];
        if (!isValidIdentifier(parent))
            throw new IllegalArgumentException(illegal);
        String arg = mi.arguments.length == 0 ? "" : emit(mi.arguments[0]);
        return parent + "." + mi.methodName + "(" + arg + ")";
    }

    private String emitBinaryOperation(Java.BinaryOperation binOp) {
        if (!allowedBinaryOps.contains(binOp.operator))
            throw new IllegalArgumentException("operator " + binOp.operator + " not allowed");
        String lh = emit(binOp.lhs);
        String rh = emitRewrittenRightHandSide(binOp);
        if (rh == null)
            rh = emit(binOp.rhs);
        return lh + " " + binOp.operator + " " + rh;
    }

    /**
     * The two conversions of the right hand side that depend on the left hand side. Returns null if neither applies
     * and the right hand side should be emitted like any other expression.
     */
    private String emitRewrittenRightHandSide(Java.BinaryOperation binOp) {
        String lhVarAsString = singleIdentifier(binOp.lhs);
        if (lhVarAsString == null)
            return null;
        if (binOp.rhs instanceof Java.StringLiteral)
            return emitStringAsIndex(binOp, lhVarAsString);
        String rhValueAsString = singleIdentifier(binOp.rhs);
        return rhValueAsString == null ? null : emitQualifiedEnumConstant(binOp, lhVarAsString, rhValueAsString);
    }

    /**
     * Replace String with its index for faster comparison (?) and skipping the Map&lt;String, Integer&gt; lookup at
     * runtime.
     */
    private String emitStringAsIndex(Java.BinaryOperation binOp, String lhVarAsString) {
        if (!lookup.hasEncodedValue(lhVarAsString))
            return null;
        requireEqualityOperator(binOp, "String");
        StringEncodedValue ev = lookup.getStringEncodedValue(lhVarAsString);
        String str = ((Java.StringLiteral) binOp.rhs).value;
        int integ = ev.indexOf(str.substring(1, str.length() - 1));
        if (integ == 0) integ = -1; // 0 means not found and this should always trigger inequality
        return "" + integ;
    }

    /**
     * Make enum explicit as NO or OTHER can occur in other enums so convert "toll == NO" to "toll == Toll.NO".
     */
    private String emitQualifiedEnumConstant(Java.BinaryOperation binOp, String lhVarAsString, String rhValueAsString) {
        if (!nameValidator.isValid(lhVarAsString) || !Helper.toUpperCase(rhValueAsString).equals(rhValueAsString))
            return null;
        requireEqualityOperator(binOp, "enum");
        emit(binOp.rhs); // validates the enum value
        return toEncodedValueClassName(lhVarAsString) + "." + rhValueAsString;
    }

    private void requireEqualityOperator(Java.BinaryOperation binOp, String comparedType) {
        if (!binOp.operator.equals("==") && !binOp.operator.equals("!="))
            throw new IllegalArgumentException("Operator " + binOp.operator + " not allowed for " + comparedType);
    }

    /**
     * The identifier of a simple name like road_class, null for anything else.
     */
    private static String singleIdentifier(Java.Rvalue rv) {
        if (rv instanceof Java.AmbiguousName && ((Java.AmbiguousName) rv).identifiers.length == 1)
            return ((Java.AmbiguousName) rv).identifiers[0];
        return null;
    }
    // ORS-GH MOD END

    static void parseExpressions(StringBuilder expressions, NameValidator nameInConditionValidator, String exceptionInfo,
                                 Set<String> createObjects, List<Statement> list, EncodedValueLookup lookup, String lastStmt) {

        for (Statement statement : list) {
            if (statement.getKeyword() == Statement.Keyword.ELSE) {
                if (!Helper.isEmpty(statement.getCondition()))
                    throw new IllegalArgumentException("expression must be empty but was " + statement.getCondition());

                expressions.append("else {" + statement.getOperation().build(statement.getValue()) + "; }\n");
            } else if (statement.getKeyword() == Statement.Keyword.ELSEIF || statement.getKeyword() == Statement.Keyword.IF) {
                ExpressionVisitor.ParseResult parseResult = parseExpression(statement.getCondition(), nameInConditionValidator, lookup);
                if (!parseResult.ok)
                    throw new IllegalArgumentException(exceptionInfo + " invalid expression \"" + statement.getCondition() + "\"" +
                            (parseResult.invalidMessage == null ? "" : ": " + parseResult.invalidMessage));
                createObjects.addAll(parseResult.guessedVariables);
                if (statement.getKeyword() == Statement.Keyword.ELSEIF)
                    expressions.append("else ");
                expressions.append("if (" + parseResult.converted + ") {" + statement.getOperation().build(statement.getValue()) + "; }\n");
            } else {
                throw new IllegalArgumentException("The clause must be either 'if', 'else_if' or 'else'");
            }
        }
        expressions.append(lastStmt);
    }

    /**
     * Enforce simple expressions of user input to increase security.
     *
     * @return ParseResult with ok if it is a valid and "simple" expression. It contains all guessed variables and a
     * converted expression that includes class names for constants to avoid conflicts e.g. when doing "toll == Toll.NO"
     * instead of "toll == NO".
     */
    static ParseResult parseExpression(String expression, NameValidator validator, EncodedValueLookup lookup) {
        ParseResult result = new ParseResult();
        // ORS-GH MOD START - security fix GHSA-mg8w-mq9r-f9c6: emit the condition from the parsed expression and
        // fail closed. Do not restore the TreeMap<Integer, Replacement> splicing that used to build "converted".
        try {
            // no file name => the message starts with "Line 1, Column 7: ..." instead of naming a pseudo file
            Parser parser = new Parser(new Scanner(null, new StringReader(expression)));
            Java.Atom atom = parser.parseConditionalExpression();
            // after parsing the expression the input should end (otherwise it is not "simple")
            if (parser.peek().type != TokenType.END_OF_INPUT)
                throw new IllegalArgumentException("expression is not simple");
            result.guessedVariables = new LinkedHashSet<>();
            ExpressionVisitor visitor = new ExpressionVisitor(result, validator, lookup);
            Java.Rvalue rvalue = atom.toRvalue();
            if (rvalue == null)
                throw new IllegalArgumentException("invalid expression " + atom);
            result.converted = new StringBuilder(visitor.emit(rvalue));
            result.ok = true;
        } catch (Exception ex) {
            // fail closed: never leave a partially built "converted" that could reach the compiler
            result.ok = false;
            result.converted = null;
            result.invalidMessage = ex.getMessage();
        }
        // ORS-GH MOD END
        return result;
    }

    static String toEncodedValueClassName(String arg) {
        if (arg.isEmpty()) throw new IllegalArgumentException("Cannot be empty");
        if (arg.endsWith(RouteNetwork.key(""))) return RouteNetwork.class.getSimpleName();
        String clazz = Helper.underScoreToCamelCase(arg);
        return Character.toUpperCase(clazz.charAt(0)) + clazz.substring(1);
    }

    static class ParseResult {
        StringBuilder converted;
        boolean ok;
        String invalidMessage;
        Set<String> guessedVariables;
    }

    interface NameValidator {
        boolean isValid(String name);
    }
}
