package io.opsit.explang.jlinerepl;

import io.opsit.explang.IParser;

public interface IREPL {
  public Object execute(IParser parser) throws Exception;

  public void setVerbose(boolean val);

  public io.opsit.explang.Compiler getCompiler();

  public void setCompiler(io.opsit.explang.Compiler compiler);

  public void setObjectWriter(IObjectWriter ow);

  public IObjectWriter getObjectWriter();
}
