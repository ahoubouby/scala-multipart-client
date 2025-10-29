# Contributing to Scala Multipart Client

First off, thank you for considering contributing to Scala Multipart Client! 🎉

It's people like you that make this library better for everyone. We welcome contributions from the community, whether you're fixing a bug, adding a feature, improving documentation, or just asking questions.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Enhancements](#suggesting-enhancements)
  - [Contributing Code](#contributing-code)
  - [Improving Documentation](#improving-documentation)
- [Development Setup](#development-setup)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Pull Request Process](#pull-request-process)
- [Community](#community)

---

## Code of Conduct

This project and everyone participating in it is governed by our Code of Conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to [ahoubouby@example.com](mailto:ahoubouby@example.com).

**Our Pledge:**
- Be respectful and inclusive
- Welcome newcomers
- Focus on what is best for the community
- Show empathy towards others

---

## How Can I Contribute?

### Reporting Bugs

**Before submitting a bug report:**
- Check the [existing issues](https://github.com/ahoubouby/scala-multipart-client/issues) to see if the problem has already been reported
- Check the [troubleshooting guide](docs/TROUBLESHOOTING.md) for common issues
- Try to isolate the problem and create a minimal reproduction

**When submitting a bug report, include:**

```markdown
**Environment:**
- Scala Multipart Client Version: [e.g., 0.1.0]
- Scala Version: [e.g., 2.13.16]
- SBT Version: [e.g., 1.9.8]
- OS: [e.g., macOS 14.0, Ubuntu 22.04]
- JVM Version: [e.g., OpenJDK 11.0.20]

**Description:**
A clear and concise description of what the bug is.

**Steps to Reproduce:**
1. Create a multipart request with...
2. Parse the response...
3. See error

**Expected Behavior:**
What you expected to happen.

**Actual Behavior:**
What actually happened.

**Code Sample:**
```scala
// Minimal code to reproduce the issue
val result = Multipart.request(httpClient)
  .post("/api/endpoint")
  .execute()
```

**Stack Trace:**
```
Full stack trace if applicable
```

**Additional Context:**
Any other context about the problem (API responses, network conditions, etc.)
```

**Bug Report Template:** Use the [bug report template](https://github.com/ahoubouby/scala-multipart-client/issues/new?template=bug_report.md) when creating an issue.

---

### Suggesting Enhancements

We love to hear your ideas for making Scala Multipart Client better!

**Before suggesting an enhancement:**
- Check if the feature already exists
- Review the [roadmap](docs/IMPROVEMENT_PLAN.md) to see if it's already planned
- Check [existing feature requests](https://github.com/ahoubouby/scala-multipart-client/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement)

**When suggesting an enhancement, include:**

```markdown
**Is your feature request related to a problem?**
A clear description of the problem. Ex. I'm always frustrated when [...]

**Describe the solution you'd like**
A clear and concise description of what you want to happen.

**Describe alternatives you've considered**
Any alternative solutions or features you've considered.

**Example Usage:**
```scala
// How would the API look?
Multipart.request(httpClient)
  .withNewFeature(...)
  .execute()
```

**Additional Context:**
Any other context, screenshots, or examples about the feature request.

**Priority:**
- [ ] Critical - Blocking production use
- [ ] High - Significant improvement
- [ ] Medium - Nice to have
- [ ] Low - Cosmetic
```

---

### Contributing Code

We welcome code contributions! Here's how to get started:

#### Quick Checklist
- [ ] Fork the repository
- [ ] Create a feature branch
- [ ] Write tests for your changes
- [ ] Ensure all tests pass
- [ ] Check code coverage (minimum 70% for new code)
- [ ] Format code with scalafmt
- [ ] Update documentation
- [ ] Write clear commit messages
- [ ] Submit a pull request

See [Development Workflow](#development-workflow) for detailed steps.

---

### Improving Documentation

Documentation improvements are always welcome! You can:

- **Fix typos and grammar** - Small fixes don't require an issue
- **Improve clarity** - Rephrase confusing sections
- **Add examples** - Real-world usage examples are valuable
- **Write guides** - Getting started, best practices, etc.
- **Update API docs** - Add Scaladoc to public APIs

**Documentation to improve:**
- README.md - Main project documentation
- docs/ - Technical documentation
- Scaladoc - API documentation
- Code comments - Inline documentation
- Examples - Working code examples

---

## Development Setup

### Prerequisites

**Required:**
- JDK 11 or higher
- SBT 1.9.8 or higher
- Git

**Optional:**
- IntelliJ IDEA with Scala plugin
- Metals (VS Code Scala support)

### Initial Setup

1. **Fork and clone the repository**
   ```bash
   # Fork on GitHub, then clone your fork
   git clone https://github.com/YOUR_USERNAME/scala-multipart-client.git
   cd scala-multipart-client
   ```

2. **Add upstream remote**
   ```bash
   git remote add upstream https://github.com/ahoubouby/scala-multipart-client.git
   ```

3. **Verify setup**
   ```bash
   # Compile the project
   sbt compile

   # Run tests
   sbt test

   # Generate coverage report
   sbt clean coverage test coverageReport
   ```

4. **Open in your IDE**
   - **IntelliJ IDEA:** Import as SBT project
   - **VS Code:** Install Metals extension, then open folder

### Project Structure

```
scala-multipart-client/
├── src/
│   ├── main/scala/com/multipart/    # Library source code
│   │   ├── api/                     # Fluent API layer
│   │   ├── client/                  # HTTP client abstractions
│   │   ├── classifier/              # Part classification
│   │   ├── model/                   # Core data models
│   │   ├── parser/                  # Multipart parser
│   │   └── utils/                   # Utilities
│   └── test/scala/com/multipart/    # Test suite
│       ├── api/
│       ├── classifier/
│       ├── model/
│       ├── parser/
│       └── utils/
├── examples/                         # Example applications
├── docs/                            # Documentation
├── project/                         # SBT build configuration
└── build.sbt                        # Build definition
```

---

## Development Workflow

### 1. Create a Feature Branch

```bash
# Update your fork
git checkout main
git pull upstream main

# Create a feature branch
git checkout -b feature/my-awesome-feature

# Or for bug fixes
git checkout -b fix/issue-123-description
```

**Branch Naming:**
- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation updates
- `refactor/description` - Code refactoring
- `test/description` - Test improvements

### 2. Make Your Changes

**Write code:**
- Follow [coding standards](#coding-standards)
- Add tests for your changes
- Update documentation as needed

**Commit frequently:**
```bash
git add .
git commit -m "feat: Add support for custom headers"
```

**Commit Message Format:**
```
<type>: <short description>

<optional detailed description>

<optional footer>
```

**Types:**
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation changes
- `style:` Code style changes (formatting, etc.)
- `refactor:` Code refactoring
- `test:` Adding or updating tests
- `chore:` Build process or tooling changes
- `perf:` Performance improvements

**Examples:**
```bash
git commit -m "feat: Add retry mechanism with exponential backoff"
git commit -m "fix: Resolve 415 error in MultipartRequestBuilder"
git commit -m "docs: Update README with coverage badges"
git commit -m "test: Add tests for PlayWSHttpClient"
```

### 3. Test Your Changes

```bash
# Run all tests
sbt test

# Run specific test
sbt "testOnly com.multipart.api.MultipartRequestBuilderSpec"

# Run tests with coverage
sbt clean coverage test coverageReport

# Open coverage report
open target/scala-2.13/scoverage-report/index.html

# Format code
sbt scalafmt

# Check formatting
sbt scalafmtCheck
```

### 4. Keep Your Branch Updated

```bash
# Update from upstream
git fetch upstream
git rebase upstream/main

# Resolve conflicts if any
git add .
git rebase --continue
```

### 5. Push Your Changes

```bash
# Push to your fork
git push origin feature/my-awesome-feature

# If you rebased, force push (use with caution)
git push --force-with-lease origin feature/my-awesome-feature
```

### 6. Create a Pull Request

1. Go to your fork on GitHub
2. Click "Compare & pull request"
3. Fill in the PR template
4. Submit the pull request

---

## Coding Standards

### Scala Style

We follow the [Scala Style Guide](https://docs.scala-lang.org/style/) with these specifics:

**Formatting:**
- Use **scalafmt** for automatic formatting
- 2-space indentation
- 100-character line limit (soft limit)
- No trailing whitespace

**Naming Conventions:**
```scala
// Classes and traits: PascalCase
class MultipartParser
trait PartClassifier

// Objects: PascalCase
object Multipart

// Methods and values: camelCase
def parseRequest(url: String): Future[MultipartResult]
val httpClient: HttpClient

// Constants: UPPER_SNAKE_CASE
val MAX_PART_SIZE = 1024 * 1024

// Type parameters: Single uppercase letter or PascalCase
def parse[T](input: T): Result
class Container[ItemType]
```

**Code Organization:**
```scala
// Order of declarations
class MyClass {
  // 1. Type members
  type ResultType = Future[MultipartResult]

  // 2. Constructor parameters
  // (in class definition)

  // 3. Abstract members
  def abstractMethod: String

  // 4. Concrete values and variables
  val config: Config = ...
  var state: State = ...

  // 5. Concrete methods
  def publicMethod(): Unit = {
    privateHelper()
  }

  // 6. Private members
  private def privateHelper(): Unit = ???
}
```

**Best Practices:**
```scala
// Use immutable collections
val parts: List[MultipartPart] = ...

// Use Option instead of null
def findPart(id: String): Option[MultipartPart]

// Use for-comprehensions for Future/Option chaining
for {
  response <- httpClient.execute(request)
  result <- MultipartParser.parse(response)
} yield result

// Pattern matching over if-else
result match {
  case Success(value) => processValue(value)
  case Failure(error) => handleError(error)
}

// Use meaningful names
val parsedParts = parse(response)  // Good
val p = parse(response)            // Bad
```

### Documentation

**Scaladoc for public APIs:**
```scala
/**
 * Parses a multipart HTTP response into structured parts.
 *
 * This method handles all standard multipart formats including
 * form-data, related, and mixed.
 *
 * @param response the HTTP response to parse
 * @param config optional parser configuration
 * @return Future containing the parsed multipart result
 * @throws ParsingException if the response is malformed
 *
 * @example {{{
 * val result = MultipartParser.parse(response)
 * result.map { multipart =>
 *   println(s"Found ${multipart.parts.size} parts")
 * }
 * }}}
 */
def parse(
  response: HttpResponse,
  config: Option[MultipartParserConfig] = None
): Future[MultipartResult]
```

**Comments for complex logic:**
```scala
// Extract boundary from Content-Type header
// Format: multipart/mixed; boundary=----boundary123
val boundary = contentType
  .split(";")
  .map(_.trim)
  .find(_.startsWith("boundary="))
  .map(_.substring("boundary=".length))
```

---

## Testing Guidelines

### Test Requirements

**Coverage Requirements:**
- New code: Minimum 70% coverage
- Modified code: Maintain or improve coverage
- Critical paths: 90%+ coverage (parsers, classifiers)

**Test Categories:**

1. **Unit Tests** (Required)
   - Test individual components in isolation
   - Use mocks for dependencies
   - Fast execution (<1 second per test)

2. **Integration Tests** (For significant features)
   - Test component interactions
   - Use test fixtures
   - Moderate execution time

3. **Property-Based Tests** (For algorithms)
   - Use ScalaCheck for properties
   - Test edge cases automatically

### Writing Tests

**Test Structure:**
```scala
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MyComponentSpec extends AnyWordSpec with Matchers {

  "MyComponent" should {

    "handle normal cases" in {
      val input = createInput()
      val result = MyComponent.process(input)

      result shouldBe expectedResult
    }

    "handle edge cases" in {
      val emptyInput = Seq.empty
      val result = MyComponent.process(emptyInput)

      result shouldBe empty
    }

    "handle errors gracefully" in {
      val invalidInput = createInvalidInput()

      intercept[ValidationException] {
        MyComponent.process(invalidInput)
      }
    }
  }
}
```

**Use Test Fixtures:**
```scala
import com.multipart.TestFixtures

class ParserSpec extends AnyWordSpec with Matchers {

  "Parser" should {
    "parse form-data" in {
      val content = TestFixtures.simpleFormData()
      val boundary = TestFixtures.simpleBoundary

      val result = parse(content, boundary)
      result.parts should have size 2
    }
  }
}
```

**Testing Async Code:**
```scala
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.duration._

class AsyncSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit val patience: PatienceConfig = PatienceConfig(
    timeout = 5.seconds,
    interval = 100.millis
  )

  "Async operation" should {
    "complete successfully" in {
      val futureResult = asyncOperation()

      whenReady(futureResult) { result =>
        result should not be empty
      }
    }
  }
}
```

### Running Tests

```bash
# Run all tests
sbt test

# Run specific test class
sbt "testOnly com.multipart.api.MultipartRequestBuilderSpec"

# Run tests matching pattern
sbt "testOnly *Parser*"

# Run tests with coverage
sbt clean coverage test coverageReport

# Run tests continuously (watch mode)
sbt ~test

# Run only failed tests
sbt testQuick
```

---

## Pull Request Process

### Before Submitting

**Checklist:**
- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] Coverage meets requirements (70%+)
- [ ] Code is formatted (scalafmt)
- [ ] Documentation is updated
- [ ] Commit messages are clear
- [ ] No merge conflicts with main

### PR Title Format

```
<type>: <description>

Examples:
feat: Add retry mechanism with exponential backoff
fix: Resolve memory leak in part parsing
docs: Update contribution guidelines
test: Add tests for HTTP client
```

### PR Description Template

```markdown
## Description
Brief description of the changes.

## Motivation
Why is this change needed? What problem does it solve?

## Changes Made
- Change 1
- Change 2
- Change 3

## Testing
How was this tested?
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Screenshots (if applicable)
Add screenshots for UI changes.

## Checklist
- [ ] Code follows style guidelines
- [ ] Tests pass locally
- [ ] Coverage requirements met
- [ ] Documentation updated
- [ ] Commit messages are clear

## Related Issues
Closes #123
Related to #456
```

### Review Process

1. **Automated Checks**
   - CI builds pass
   - Tests pass
   - Coverage checks pass
   - Code formatting validated

2. **Code Review**
   - Maintainers review your code
   - Address feedback
   - Update PR as needed

3. **Approval**
   - At least one approval from maintainers
   - No unresolved conversations

4. **Merge**
   - Squash and merge (default)
   - Rebase and merge (for clean history)
   - Merge commit (for feature branches)

### After Merge

- Delete your feature branch
- Update your fork
- Celebrate! 🎉

---

## Community

### Getting Help

**Questions?**
- 💬 [GitHub Discussions](https://github.com/ahoubouby/scala-multipart-client/discussions) - Ask questions, share ideas
- 🐛 [GitHub Issues](https://github.com/ahoubouby/scala-multipart-client/issues) - Report bugs
- 📧 Email: [ahoubouby@example.com](mailto:ahoubouby@example.com)

**Resources:**
- [Documentation](docs/) - Technical docs
- [Examples](examples/) - Working examples
- [Improvement Plan](docs/IMPROVEMENT_PLAN.md) - Roadmap

### Recognition

Contributors are recognized in:
- GitHub Contributors page
- Release notes
- CHANGELOG.md

### License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

## Quick Reference

### Common Commands

```bash
# Development
sbt compile                    # Compile code
sbt test                       # Run tests
sbt coverage test coverageReport  # Coverage report
sbt scalafmt                   # Format code
sbt ~test                      # Watch mode

# Git workflow
git checkout -b feature/name   # Create branch
git add .                      # Stage changes
git commit -m "type: message"  # Commit
git push origin feature/name   # Push

# Keep updated
git fetch upstream             # Fetch updates
git rebase upstream/main       # Rebase
git push --force-with-lease    # Force push
```

### Coverage Requirements

- New code: 70% minimum
- Critical paths: 90% minimum
- Overall target: 85%+

### Code Quality

- Format with scalafmt
- Follow Scala style guide
- Add Scaladoc for public APIs
- Write meaningful tests

---

**Thank you for contributing to Scala Multipart Client!** 🚀

Your contributions make this library better for everyone. We appreciate your time and effort!

Questions? Feel free to reach out or open a discussion. We're here to help!
