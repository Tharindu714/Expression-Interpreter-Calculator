import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

/**
 * Calculator — Interpreter (Shunting-yard + AST) Demo
 * Single-file Java Swing app that parses integer arithmetic with + and - (infix),
 * builds an AST using the Interpreter pattern, evaluates step-by-step, and
 * is extensible so new operations (e.g., *, /, POWER) can be added without
 * modifying the parser.

 * Key ideas:
 *  - Tokenizer -> Shunting-yard to convert to RPN (supports precedence & associativity)
 *  - Build AST from RPN using OperatorRegistry (factory/registry for operators)
 *  - Expression interface with NumberExpression and BinaryExpression subclasses
 *  - OperatorRegistry holds creation logic and runtime evaluator; new operators can be registered
 *  - GUI shows input, tokenization, RPN, evaluation steps and final result in a math-themed UI

 * Antipattern rules applied:
 *  - Avoid hard-coded if/else chains for operators: use a registry map instead.
 *  - Keep operator metadata (precedence/associativity/arity) with the operator entry.
 *  - Keep Expression nodes immutable where appropriate.
 *  - Do not duplicate parsing logic across code; single parser pipeline (tokenize -> RPN -> AST).

 * Run:
 * javac Calculator_Interpreter_GUI.java * Calculator_Interpreter_GUI
 */
public class Calculator_Interpreter_GUI {
    public static void main(String[] args) {
        // register core operators
        OperatorRegistry.register("+", 1, false, 2,
                argsList -> new AddExpression(argsList.get(0), argsList.get(1)),
                Double::sum);
        OperatorRegistry.register("-", 1, false, 2,
                argsList -> new SubtractExpression(argsList.get(0), argsList.get(1)),
                (a, b) -> a - b);
        // multiplication/division can be added later with higher precedence
        SwingUtilities.invokeLater(() -> new CalcFrame().setVisible(true));
    }
}

/* -------------------- Interpreter core -------------------- */
interface Expression {
    double interpret() throws Exception;
}

class NumberExpression implements Expression {
    private final double value;
    public NumberExpression(double value) { this.value = value; }
    @Override public double interpret() { return value; }
    @Override public String toString() { return String.valueOf((long)value); }
}

abstract class BinaryExpression implements Expression {
    protected final Expression left, right;
    public BinaryExpression(Expression left, Expression right) { this.left = left; this.right = right; }
}

class AddExpression extends BinaryExpression {
    public AddExpression(Expression left, Expression right) { super(left, right); }
    @Override public double interpret() throws Exception { return left.interpret() + right.interpret(); }
    @Override public String toString() { return "(" + left + " + " + right + ")"; }
}

class SubtractExpression extends BinaryExpression {
    public SubtractExpression(Expression left, Expression right) { super(left, right); }
    @Override public double interpret() throws Exception { return left.interpret() - right.interpret(); }
    @Override public String toString() { return "(" + left + " - " + right + ")"; }
}

/* -------------------- Operator Registry (extensible) -------------------- */
interface ExpressionCreator {
    Expression create(List<Expression> args) throws Exception;
}

class OperatorEntry {
    final int precedence;
    final boolean rightAssociative;
    final int arity;
    final ExpressionCreator creator;
    final DoubleBinaryOperator evaluator; // runtime evaluator for step-by-step

    public OperatorEntry(int precedence, boolean rightAssociative, int arity, ExpressionCreator creator, DoubleBinaryOperator evaluator) {
        this.precedence = precedence; this.rightAssociative = rightAssociative; this.arity = arity; this.creator = creator; this.evaluator = evaluator;
    }
}

class OperatorRegistry {
    private static final Map<String, OperatorEntry> registry = new HashMap<>();
    public static void register(String symbol, int precedence, boolean rightAssoc, int arity, ExpressionCreator creator, DoubleBinaryOperator evaluator) {
        registry.put(symbol, new OperatorEntry(precedence, rightAssoc, arity, creator, evaluator));
    }
    public static Optional<OperatorEntry> get(String symbol) { return Optional.ofNullable(registry.get(symbol)); }
    public static boolean isOperator(String s) { return registry.containsKey(s); }
}

/* -------------------- Tokenizer & Parser (Shunting-yard) -------------------- */
enum TokenType { NUMBER, OPERATOR, LEFT_PAREN, RIGHT_PAREN }
class Token {
    final TokenType type; final String text;
    Token(TokenType t, String txt) { type = t; text = txt; }
    public String toString() { return text; }
}

class Parser {
    public static List<Token> tokenize(String s) throws Exception {
        List<Token> out = new ArrayList<>();
        String input = s.trim();
        int i = 0;
        Token last = null;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }

            if (c == '(') { out.add(new Token(TokenType.LEFT_PAREN, "(")); last = out.get(out.size()-1); i++; continue; }
            if (c == ')') { out.add(new Token(TokenType.RIGHT_PAREN, ")")); last = out.get(out.size()-1); i++; continue; }

            // number or unary minus
            if (Character.isDigit(c) || (c == '-' && (last == null || last.type == TokenType.OPERATOR || last.type == TokenType.LEFT_PAREN) && i+1 < input.length() && Character.isDigit(input.charAt(i+1)))) {
                int j = i;
                if (input.charAt(j) == '-') j++; // include leading minus
                while (j < input.length() && Character.isDigit(input.charAt(j))) j++;
                String num = input.substring(i, j);
                out.add(new Token(TokenType.NUMBER, num));
                last = out.get(out.size()-1);
                i = j; continue;
            }

            // operator symbols
            String op = String.valueOf(c);
            if (OperatorRegistry.isOperator(op)) {
                out.add(new Token(TokenType.OPERATOR, op)); last = out.get(out.size()-1); i++; continue;
            }

            throw new Exception("Unknown token at position " + i + ": '" + c + "'");
        }
        return out;
    }

    // shunting-yard: convert tokens to RPN tokens
    public static List<Token> toRPN(List<Token> tokens) throws Exception {
        List<Token> output = new ArrayList<>();
        Deque<Token> opStack = new ArrayDeque<>();
        for (Token t : tokens) {
            switch (t.type) {
                case NUMBER: output.add(t); break;
                case OPERATOR:
                    OperatorEntry o1 = OperatorRegistry.get(t.text).orElseThrow(() -> new Exception("Unknown operator " + t.text));
                    while (!opStack.isEmpty() && opStack.peek().type == TokenType.OPERATOR) {
                        OperatorEntry o2 = OperatorRegistry.get(opStack.peek().text).get();
                        if ((o1.rightAssociative && o1.precedence < o2.precedence) || (!o1.rightAssociative && o1.precedence <= o2.precedence)) {
                            output.add(opStack.pop());
                        } else break;
                    }
                    opStack.push(t);
                    break;
                case LEFT_PAREN: opStack.push(t); break;
                case RIGHT_PAREN:
                    while (!opStack.isEmpty() && opStack.peek().type != TokenType.LEFT_PAREN) output.add(opStack.pop());
                    if (opStack.isEmpty() || opStack.peek().type != TokenType.LEFT_PAREN) throw new Exception("Mismatched parentheses");
                    opStack.pop(); // pop left paren
                    break;
            }
        }
        while (!opStack.isEmpty()) {
            Token tk = opStack.pop();
            if (tk.type == TokenType.LEFT_PAREN || tk.type == TokenType.RIGHT_PAREN) throw new Exception("Mismatched parentheses");
            output.add(tk);
        }
        return output;
    }

    // build AST from RPN tokens using OperatorRegistry
    public static Expression buildASTFromRPN(List<Token> rpn) throws Exception {
        Deque<Expression> stack = new ArrayDeque<>();
        for (Token t : rpn) {
            if (t.type == TokenType.NUMBER) {
                double val = Double.parseDouble(t.text);
                stack.push(new NumberExpression(val));
            } else if (t.type == TokenType.OPERATOR) {
                OperatorEntry entry = OperatorRegistry.get(t.text).orElseThrow(() -> new Exception("Unknown operator in RPN: " + t.text));
                if (stack.size() < entry.arity) throw new Exception("Insufficient operands for operator " + t.text);
                List<Expression> args = new ArrayList<>();
                for (int i = 0; i < entry.arity; i++) args.add(stack.pop());
                // popped in reverse: last popped is right operand
                Collections.reverse(args);
                Expression node = entry.creator.create(args);
                stack.push(node);
            } else {
                throw new Exception("Invalid token in RPN: " + t.text);
            }
        }
        if (stack.size() != 1) throw new Exception("Invalid expression. Stack size=" + stack.size());
        return stack.pop();
    }

    // For demonstration: evaluate RPN step-by-step using registry evaluators and produce steps
    public static List<String> evaluateRPNWithSteps(List<Token> rpn) throws Exception {
        List<String> steps = new ArrayList<>();
        Deque<Double> st = new ArrayDeque<>();
        for (Token t : rpn) {
            if (t.type == TokenType.NUMBER) {
                st.push(Double.parseDouble(t.text));
                steps.add("Push " + t.text);
            } else if (t.type == TokenType.OPERATOR) {
                OperatorEntry entry = OperatorRegistry.get(t.text).orElseThrow(() -> new Exception("Unknown operator " + t.text));
                if (st.size() < entry.arity) throw new Exception("Insufficient operands for " + t.text);
                double[] args = new double[entry.arity];
                for (int i = entry.arity - 1; i >= 0; i--) args[i] = st.pop();
                double res;
                if (entry.evaluator == null) {
                    // fallback: build AST for this tiny operation
                    List<Expression> exprArgs = new ArrayList<>();
                    for (double d : args) exprArgs.add(new NumberExpression(d));
                    res = entry.creator.create(exprArgs).interpret();
                } else {
                    res = entry.evaluator.applyAsDouble(args[0], args[1]);
                }
                StringBuilder sb = new StringBuilder();
                sb.append("Apply ").append(t.text).append(" on ");
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append((long)args[i]);
                }
                sb.append(" => ").append((long)res);
                steps.add(sb.toString());
                st.push(res);
            }
        }
        if (st.size() != 1) throw new Exception("Evaluation ended with stack size=" + st.size());
        steps.add("Result: " + st.peek().longValue());
        return steps;
    }
}

/* -------------------- GUI -------------------- */
class CalcFrame extends JFrame {
    private final JTextField inputField = new JTextField();
    private final DefaultListModel<String> tokensModel = new DefaultListModel<>();
    private final DefaultListModel<String> rpnModel = new DefaultListModel<>();
    private final DefaultListModel<String> stepsModel = new DefaultListModel<>();
    private final JLabel resultLabel = new JLabel(" ");

    public CalcFrame() {
        setTitle("CalcFlow — Interpreter Calculator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 560);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(new HeaderPanel(), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1,3,12,12));
        center.setBorder(new EmptyBorder(12,12,12,12));

        // Left: input & tokens
        JPanel left = new JPanel(new BorderLayout());
        left.setBorder(BorderFactory.createTitledBorder("Input & Tokens"));
        inputField.setFont(new Font("Poppins", Font.PLAIN, 18));
        inputField.setText("10-5+8+2");
        left.add(inputField, BorderLayout.NORTH);
        JList<String> tokensList = new JList<>(tokensModel);
        left.add(new JScrollPane(tokensList), BorderLayout.CENTER);
        center.add(left);

        // Middle: RPN
        JPanel mid = new JPanel(new BorderLayout());
        mid.setBorder(BorderFactory.createTitledBorder("RPN (Reverse Polish Notation)"));
        JList<String> rpnList = new JList<>(rpnModel);
        mid.add(new JScrollPane(rpnList), BorderLayout.CENTER);
        center.add(mid);

        // Right: steps
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createTitledBorder("Evaluation Steps"));
        JList<String> stepsList = new JList<>(stepsModel);
        right.add(new JScrollPane(stepsList), BorderLayout.CENTER);
        center.add(right);

        add(center, BorderLayout.CENTER);

        // Bottom controls
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setBorder(new EmptyBorder(6,12,12,12));
        JButton evalBtn = new JButton("Evaluate");
        JButton clearBtn = new JButton("Clear");
        bottom.add(evalBtn);
        bottom.add(clearBtn);
        bottom.add(new JLabel("  Result:"));
        resultLabel.setFont(new Font("Inter", Font.BOLD, 22));
        resultLabel.setOpaque(true);
        resultLabel.setBackground(new Color(255,255,255));
        bottom.add(resultLabel);
        add(bottom, BorderLayout.SOUTH);

        evalBtn.addActionListener(e -> onEvaluate());
        clearBtn.addActionListener(e -> onClear());
        inputField.addActionListener(e -> onEvaluate());
    }

    private void onEvaluate() {
        tokensModel.clear(); rpnModel.clear(); stepsModel.clear(); resultLabel.setText(" ");
        String input = inputField.getText().trim();
        try {
            List<Token> tokens = Parser.tokenize(input);
            tokens.forEach(t -> tokensModel.addElement(t.type + " : " + t.text));
            List<Token> rpn = Parser.toRPN(tokens);
            rpn.forEach(t -> rpnModel.addElement(t.text));
            List<String> steps = Parser.evaluateRPNWithSteps(rpn);
            steps.forEach(stepsModel::addElement);
            // build AST and evaluate final
            Expression ast = Parser.buildASTFromRPN(rpn);
            double res = ast.interpret();
            resultLabel.setText(String.valueOf((long)res));
            resultLabel.setBackground(new Color(200, 255, 200));
        } catch (Exception ex) {
            resultLabel.setText("Error: " + ex.getMessage());
            resultLabel.setBackground(new Color(255, 200, 200));
            stepsModel.addElement("Error: " + ex.getMessage());
        }
    }

    private void onClear() {
        inputField.setText(""); tokensModel.clear(); rpnModel.clear(); stepsModel.clear(); resultLabel.setText(" ");
    }
}

class HeaderPanel extends JPanel {
    public HeaderPanel() { setPreferredSize(new Dimension(100,110)); }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        int w = getWidth(), h = getHeight();
        GradientPaint gp = new GradientPaint(0,0,new Color(10,40,120), w, h, new Color(40,170,200));
        g2.setPaint(gp); g2.fillRect(0,0,w,h);

        g2.setColor(Color.white);
        g2.setFont(new Font("Poppins", Font.BOLD, 24));
        g2.drawString("CalcFlow — Expression Interpreter", 18, 36);
        g2.setFont(new Font("Inter", Font.PLAIN, 13));
        g2.drawString("Enter expressions with integers, + and -. Extensible — add * or / later by registering operators.", 18, 58);

        g2.setFont(new Font("Serif", Font.BOLD, 40));
        g2.drawString("∑", w - 80, 60);
    }
}