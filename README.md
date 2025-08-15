# 🧮 CalcFlow — Expression Interpreter Calculator

<p align="center">
  <img width="886" height="553" alt="GUI" src="https://github.com/user-attachments/assets/643a0854-32ef-44c6-a8dc-8b47ae1647da" />
</p>

A colourful, educational Java Swing demo that parses integer expressions (infix) containing `+` and `-`, builds an AST using the **Interpreter** pattern, evaluates step-by-step, and displays tokens, RPN, evaluation steps and the final result. The design is extensible so new operations (e.g. `*`, `/`, `^`) can be added by *registering* them — no parser edits required.

---

## ✨ Highlights

* Parser pipeline: **Tokenizer → Shunting-yard (RPN) → AST (Interpreter)**.
* Clean, extensible **OperatorRegistry**: add new operators without changing parsing logic.
* GUI shows: input, tokens, RPN, evaluation steps and final result in real time.
* Anti-pattern rules applied: no sprawling `if/else` for ops, immutable expression nodes, single parsing pipeline, and a registry-based operator system.

---

## 📁 Repository

`https://github.com/Tharindu714/Expression-Interpreter-Calculator.git`

Project entrypoint (single-file demo): `Calculator_Interpreter_GUI.java`

---

## 🛠️ Build & Run

```bash
# from repo root
javac Calculator_Interpreter_GUI.java
java Calculator_Interpreter_GUI
```

> Java 8+ recommended.

---

## 🔍 What the UI shows

* **Input**: type an expression such as `10-5+8+2` (supports unary `-`, parentheses).
* **Tokens**: list of tokens produced by the tokenizer.
* **RPN**: Reverse Polish Notation computed by the shunting-yard algorithm.
* **Evaluation Steps**: RPN evaluation trace (push/apply operations) for clarity.
* **Result**: final evaluated value.

---

## 🧩 Design Overview

**Core components**

* `Expression` (interface): `interpret()` — implemented by `NumberExpression`, `BinaryExpression` subclasses (`AddExpression`, `SubtractExpression`, ...).
* `OperatorRegistry`: holds operator metadata (symbol, precedence, associativity, arity) and factory/creator lambdas.
* `Parser`: tokenizes input, converts to RPN with shunting-yard, builds AST from RPN and provides step-by-step evaluation.
* `CalcFrame`: Swing GUI that ties everything together.

**Extensibility**: Add new operator (e.g., `*`) by a single `OperatorRegistry.register(...)` call that specifies precedence and a creator — parser remains untouched.

---

## ✅ Anti-patterns avoided

* ❌ **No big `if/else` chains** for operator handling. Use a registry map instead.
* ❌ **No duplicated parsing logic** across modules — single pipeline.
* ✅ **Immutable expression nodes** where possible to avoid shared mutable state.
* ⚠️ **Cache & concurrency caution**: registry in the demo is a simple `HashMap`. For multi-threaded servers use `ConcurrentHashMap`.

---

## 🔧 How to add a new operator (example: multiplication)

Add this to the initialization section (before launching the GUI):

```java
OperatorRegistry.register("*", 2, false, 2,
    args -> new MultiplyExpression(args.get(0), args.get(1)),
    (a,b) -> a * b);
```

This registers `*` with higher precedence (2). The parser & shunting-yard automatically respect the precedence when creating the RPN and AST.

---

## 📐 Diagrams

**UML class diagram**

<p align="center">
  <img width="2570" height="1013" alt="UML-Light" src="https://github.com/user-attachments/assets/3da26372-acee-49c3-9777-96c5e98fe9a2" />
</p>

**Sequence / Evaluate flow**

<p align="center">
  <img width="898" height="719" alt="Seq-Light" src="https://github.com/user-attachments/assets/b02bbc52-1abb-4744-98a0-d2601820f173" />
</p>

---

## 🧪 Example

Input: `10-5+8+2`

* Tokens: `10, -, 5, +, 8, +, 2`
* RPN: `10 5 - 8 + 2 +`
* Steps:

  * Push 10
  * Push 5
  * Apply - on 10,5 => 5
  * Push 8
  * Apply + on 5,8 => 13
  * Push 2
  * Apply + on 13,2 => 15
* Result: `15`

---

## 📸 Screenshots & Assets

<p align="center">
  
  ![Scenario 7](https://github.com/user-attachments/assets/25189b1e-46ce-43f3-a86e-7dee7cf10a98)
  
</p>

---

## 📝 License

MIT — feel free to reuse and adapt. If you improve the parser or add operators, please consider adding tests and PRs.

---

Made with ❤️ and careful parsing — Tharindu
