package com.jpetrak.gate.java.gui;

import com.jpetrak.gate.java.JavaCodeDriven;
import com.jpetrak.gate.java.JavaScriptingPR;
import gate.Resource;
import gate.creole.AbstractVisualResource;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.GuiType;
import java.awt.GridLayout;
import java.io.File;


/**
 *
 * @author johann
 */
@CreoleResource(
  name = "Java Code Editor", 
  comment = "Editor for Java code", 
  guiType = GuiType.LARGE, 
  mainViewer = true, 
  resourceDisplayed = "com.jpetrak.gate.java.JavaCodeDriven")
public class JavaEditorVR extends AbstractVisualResource 
{
  
  protected JavaEditorPanel panel;
  protected JavaCodeDriven theTarget;
  protected JavaScriptingPR pr = null;
  
  @Override
  public void setTarget(Object target) {
    if(target instanceof JavaCodeDriven) {
      //System.out.println("Found a JavaCodeDriven, activating panel");
      theTarget = (JavaCodeDriven)target;
      panel = new JavaEditorPanel();
      this.add(panel);
      this.setLayout(new GridLayout(1,1));
      // register ourselves as the EditorVR
      pr = (JavaScriptingPR)target;
      pr.registerEditorVR(this);
      panel.setPR(pr);
      panel.setFile(pr.getJavaProgramFile());
      if(pr.isCompileError) {
        panel.setCompilationError();
      } else {
        panel.setCompilationOk();
      }
    } else {
      //System.out.println("Not a JavaCodeDriven: "+((Resource)target).getName());
    }
  }
  
  public void setFile(File file) {
    panel.setFile(file);
  }
  
  public void setCompilationError() {
    panel.setCompilationError();
  }
  public void setCompilationOk() {
    panel.setCompilationOk();
  }
  
}
