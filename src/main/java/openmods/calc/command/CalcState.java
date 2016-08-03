package openmods.calc.command;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import openmods.calc.Calculator;
import openmods.calc.ExprType;
import openmods.calc.FixedSymbol;
import openmods.calc.ICalculatorFrame;
import openmods.calc.ISymbol;
import openmods.calc.IValuePrinter;
import openmods.calc.StackValidationException;
import openmods.calc.types.bigint.BigIntCalculatorFactory;
import openmods.calc.types.fp.DoubleCalculatorFactory;
import openmods.calc.types.fraction.FractionCalculatorFactory;
import openmods.utils.Stack;
import org.apache.commons.lang3.math.Fraction;

public class CalcState {

	public static class NoSuchNameException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public NoSuchNameException(String message) {
			super(message);
		}
	}

	private static interface IFunction<E> {
		public E call();
	}

	private static class SenderHolder {
		private ICommandSender sender;

		public int getX() {
			Preconditions.checkNotNull(sender, "DERP");
			return sender.getPlayerCoordinates().posX;
		}

		public int getY() {
			Preconditions.checkNotNull(sender, "DERP");
			return sender.getPlayerCoordinates().posY;
		}

		public int getZ() {
			Preconditions.checkNotNull(sender, "DERP");
			return sender.getPlayerCoordinates().posZ;
		}

		public <E> Calculator<E, ExprType> addPrinter(Calculator<E, ExprType> calculator) {
			final IValuePrinter<E> printer = calculator.printer;
			calculator.environment.setGlobalSymbol("p", new ISymbol<E>() {
				@Override
				public void execute(ICalculatorFrame<E> frame, Optional<Integer> argumentsCount, Optional<Integer> returnsCount) {
					Preconditions.checkNotNull(sender, "DERP");

					if (returnsCount.isPresent() && returnsCount.get() != 0) throw new StackValidationException("This function does not return any values");

					final Stack<E> stack = frame.stack();
					final int in = argumentsCount.or(1);

					final List<String> results = Lists.newArrayListWithExpectedSize(in);
					for (int i = 0; i < in; i++) {
						final E value = stack.pop();
						results.add(printer.toString(value));
					}

					final String result = ": " + Joiner.on(" ").join(results);
					sender.addChatMessage(new ChatComponentText(result));
				}
			});
			return calculator;
		}

		public synchronized <E> E call(ICommandSender sender, IFunction<E> function) {
			this.sender = sender;
			final E result = function.call();
			this.sender = null;
			return result;
		}
	}

	public enum CalculatorType {
		DOUBLE {
			@Override
			public Calculator<?, ExprType> newCalculator(final SenderHolder holder) {
				final Calculator<Double, ExprType> calculator = DoubleCalculatorFactory.createDefault();

				calculator.environment.setGlobalSymbol("$x", new FixedSymbol<Double>(0, 1) {
					@Override
					public void execute(ICalculatorFrame<Double> frame) {
						frame.stack().push(Double.valueOf(holder.getX()));
					}
				});

				calculator.environment.setGlobalSymbol("$y", new FixedSymbol<Double>(0, 1) {
					@Override
					public void execute(ICalculatorFrame<Double> frame) {
						frame.stack().push(Double.valueOf(holder.getY()));
					}
				});

				calculator.environment.setGlobalSymbol("$z", new FixedSymbol<Double>(0, 1) {
					@Override
					public void execute(ICalculatorFrame<Double> frame) {
						frame.stack().push(Double.valueOf(holder.getZ()));
					}
				});

				return calculator;
			}
		},
		FRACTION {
			@Override
			public Calculator<?, ExprType> newCalculator(final SenderHolder holder) {
				final Calculator<Fraction, ExprType> calculator = FractionCalculatorFactory.createDefault();

				calculator.environment.setGlobalSymbol("$x", new FixedSymbol<Fraction>(0, 1) {
					@Override
					public void execute(ICalculatorFrame<Fraction> frame) {
						frame.stack().push(Fraction.getFraction(holder.getX(), 1));
					}
				});

				calculator.environment.setGlobalSymbol("$y", new FixedSymbol<Fraction>(0, 1) {
					@Override
					public void execute(ICalculatorFrame<Fraction> frame) {
						frame.stack().push(Fraction.getFraction(holder.getY(), 1));
					}
				});

				calculator.environment.setGlobalSymbol("$z", new FixedSymbol<Fraction>(0, 1) {
					@Override
					public void execute(ICalculatorFrame<Fraction> frame) {
						frame.stack().push(Fraction.getFraction(holder.getZ(), 1));
					}
				});

				return calculator;
			}
		},
		BIGINT {
			@Override
			public Calculator<?, ExprType> newCalculator(final SenderHolder holder) {
				final Calculator<BigInteger, ExprType> calculator = BigIntCalculatorFactory.createDefault();

				calculator.environment.setGlobalSymbol("$x", new FixedSymbol<BigInteger>(0, 1) {
					@Override
					public void execute(ICalculatorFrame<BigInteger> frame) {
						frame.stack().push(BigInteger.valueOf(holder.getX()));
					}
				});

				calculator.environment.setGlobalSymbol("$y", new FixedSymbol<BigInteger>(0, 1) {
					@Override
					public void execute(ICalculatorFrame<BigInteger> frame) {
						frame.stack().push(BigInteger.valueOf(holder.getY()));
					}
				});

				calculator.environment.setGlobalSymbol("$z", new FixedSymbol<BigInteger>(0, 1) {
					@Override
					public void execute(ICalculatorFrame<BigInteger> frame) {
						frame.stack().push(BigInteger.valueOf(holder.getZ()));
					}
				});

				return calculator;
			}
		};

		public Calculator<?, ExprType> createCalculator(SenderHolder holder) {
			return holder.addPrinter(newCalculator(holder));
		}

		protected abstract Calculator<?, ExprType> newCalculator(SenderHolder holder);
	}

	private final SenderHolder senderHolder = new SenderHolder();

	private Calculator<?, ExprType> active = CalculatorType.DOUBLE.createCalculator(senderHolder);

	private Calculator<?, ExprType> prev;

	public ExprType exprType = ExprType.INFIX;

	private Stack<Calculator<?, ExprType>> calculatorStack = Stack.create();

	private Map<String, Calculator<?, ExprType>> calculatorMap = Maps.newHashMap();

	public Calculator<?, ExprType> getActiveCalculator() {
		return active;
	}

	private void setActiveCalculator(final Calculator<?, ExprType> newCalculator) {
		prev = active;
		active = newCalculator;
	}

	public void restorePreviousCalculator() {
		final Calculator<?, ExprType> tmp = active;
		active = prev;
		prev = tmp;
	}

	public void createCalculator(CalculatorType type) {
		setActiveCalculator(type.createCalculator(senderHolder));
	}

	public int pushCalculator() {
		calculatorStack.push(active);
		return calculatorStack.size();
	}

	public int popCalculator() {
		setActiveCalculator(calculatorStack.pop());
		return calculatorStack.size();
	}

	public void nameCalculator(String name) {
		calculatorMap.put(name, active);
	}

	public Set<String> getCalculatorsNames() {
		return Collections.unmodifiableSet(calculatorMap.keySet());
	}

	public void loadCalculator(String name) {
		final Calculator<?, ExprType> newCalculator = calculatorMap.get(name);
		if (newCalculator == null) throw new NoSuchNameException(name);
		setActiveCalculator(newCalculator);
	}

	public void compileAndExecute(ICommandSender sender, final String expr) {
		senderHolder.call(sender, new IFunction<Void>() {
			@Override
			public Void call() {
				active.compileAndExecute(exprType, expr);
				return null;
			}
		});
	}

	public String compileExecuteAndPrint(ICommandSender sender, final String expr) {
		return senderHolder.call(sender, new IFunction<String>() {
			@Override
			public String call() {
				return active.compileExecuteAndPrint(exprType, expr);
			}
		});
	}

	public Object compileAndSetGlobalSymbol(ICommandSender sender, final String id, final String expr) {
		return senderHolder.call(sender, new IFunction<Object>() {
			@Override
			public Object call() {
				return active.compileAndSetGlobalSymbol(exprType, id, expr);
			}
		});
	}

	public void compileAndDefineGlobalFunction(ICommandSender sender, final String id, final int argCount, final String expr) {
		senderHolder.call(sender, new IFunction<Void>() {
			@Override
			public Void call() {
				active.compileAndDefineGlobalFunction(exprType, id, argCount, expr);
				return null;
			}
		});

	}

}
