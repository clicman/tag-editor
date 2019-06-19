package cucumber.examples.java.calculator;

import cucumber.api.TypeRegistry;
import cucumber.api.java.en.And;
import io.cucumber.cucumberexpressions.ParameterType;

public class ParameterTypeSteps {
  @And("today is {iso-date}")
  public void step_method(Date arg1) throws Throwable {
    System.out.println("text");
  }

  @And("{int} is int")
  public void step_method(int arg1) throws Throwable {
  }

  @And("{float} is float")
  public void step_method(float arg1) throws Throwable {
  }

  @And("{word} is word")
  public void step_method(String arg1) throws Throwable {
  }

  @And("{string} is string")
  public void step_string_method(String arg1) throws Throwable {
  }

  @Override
  public void configureTypeRegistry(TypeRegistry typeRegistry) {
    typeRegistry.defineParameterType(new ParameterType<>(
      "iso-date",
      "\\d{4}-\\d{2}-\\d{2}",
      Date.class,
      (String s) -> new SimpleDateFormat("yyyy-mm-dd").parse(s)
    ));
  }

  @And("There is bigdecimal {bigdecimal}")
  public void step_method(BigDecimal param) throws Throwable {
  }

  @And("There is short {short}")
  public void step_method(short arg1) throws Throwable {
  }

  @And("There is biginteger {biginteger}")
  public void step_method(BigInteger param) throws Throwable {
  }

  @And("There is long {long}")
  public void step_method(Long arg1) throws Throwable {
  }

  @And("There is byte {byte}")
  public void step_method(byte arg1) throws Throwable {
  }

  @And("There is double {double}")
  public void step_method(double arg1) throws Throwable {
  }

  @And("I have {int} cucumber(s) in my belly")
  public void iHaveCucumbersInMyBelly(int arg1) throws Throwable {
  }
}
