
package org.area515.resinprinter.services;

import java.awt.GraphicsDevice;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.area515.resinprinter.display.AlreadyAssignedException;
import org.area515.resinprinter.display.DisplayManager;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.printer.BuildDirection;
import org.area515.resinprinter.printer.MachineConfig;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.printer.PrinterConfiguration;
import org.area515.resinprinter.printer.PrinterManager;
import org.area515.resinprinter.printer.SlicingProfile;
import org.area515.resinprinter.printer.MachineConfig.ComPortSettings;
import org.area515.resinprinter.printer.MachineConfig.MonitorDriverConfig;
import org.area515.resinprinter.printer.MachineConfig.MotorsDriverConfig;
import org.area515.resinprinter.printer.SlicingProfile.InkConfig;
import org.area515.resinprinter.serial.ConsoleCommPort;
import org.area515.resinprinter.server.HostProperties;

@Path("printers")
public class PrinterService {
	
	public static PrinterService INSTANCE = new PrinterService();
	
	private PrinterService(){}
	
	 @GET
	 @Path("list")
	 @Produces(MediaType.APPLICATION_JSON)
	 public List<Printer> getPrinters() {
		 List<PrinterConfiguration> identifiers = HostProperties.Instance().getPrinterConfigurations();
		 List<Printer> printers = new ArrayList<Printer>();
		 for (PrinterConfiguration current : identifiers) {
			try {
				Printer printer = PrinterManager.Instance().getPrinter(current.getName());
				if (printer == null) {
					printer = new Printer(current);
				}
				printers.add(printer);
			} catch (InappropriateDeviceException e) {
				e.printStackTrace();
			}
		 }
		 
		 return printers;
	 }
	 
	 @GET
	 @POST
	 @Path("deletePrinter/{printername}")
	 @Produces(MediaType.APPLICATION_JSON)
	 public MachineResponse deletePrinter(@PathParam("printername") String printerName) {
			try {
				Printer currentPrinter = PrinterManager.Instance().getPrinter(printerName);
				if (currentPrinter != null) {
					throw new InappropriateDeviceException("Can't delete printer when it's started:" + printerName);
				}

				PrinterConfiguration currentConfiguration = HostProperties.Instance().getPrinterConfiguration(printerName);
				if (currentConfiguration == null) {
					throw new InappropriateDeviceException("No printer with that name:" + printerName);
				}				
				
				HostProperties.Instance().removePrinterConfiguration(currentConfiguration);
				return new MachineResponse("delete", true, "Deleted:" + printerName);
			} catch (InappropriateDeviceException e) {
				e.printStackTrace();
				return new MachineResponse("delete", false, e.getMessage());
			}
	 }
	 
	 @GET
	 @Path("createTemplatePrinter")
	 @Produces(MediaType.APPLICATION_JSON)
	 public Printer createTemplatePrinter() {
		 PrinterConfiguration configuration = createTemplatePrinter(
				 "CWH Template Printer", //"mUVe 1 DLP (Testing)", 
				 DisplayManager.SIMULATED_DISPLAY, 
				 ConsoleCommPort.CONSOLE_COMM_PORT, 
				 134, 75, 185);
		 configuration.getSlicingProfile().getSelectedInkConfig().setNumberOfFirstLayers(10);
		 configuration.getSlicingProfile().getSelectedInkConfig().setFirstLayerExposureTime(20000);
		 configuration.getSlicingProfile().getSelectedInkConfig().setExposureTime(8000);
		 configuration.getSlicingProfile().setgCodeHeader(
				 "G21 ;Set units to be mm\n" +
				 "G91 ;Relative Positioning\n" +
				 "G28 ; Home Printer\n" +
				 "M650 D$ZLiftDist S$ZLiftRate P0; CWH Template Preferences\n" + //mUVe 1 Prefs\n" +
				 "M17 ;Enable motors");
		 configuration.getSlicingProfile().setgCodeFooter(
				 "M18 ;Disable Motors");
		 configuration.getSlicingProfile().setgCodeLift(
				 "M651; Do CWH Template Peel Move\n" + //Do mUVe 1 Peel Move\n" + 
				 "G1 Z${((LayerThickness) * ZDir)}");
		 configuration.getSlicingProfile().setZLiftDistanceGCode("M650 D${ZLiftDist} S${ZLiftRate}");
		 configuration.getSlicingProfile().setZLiftSpeedGCode("M650 D${ZLiftDist} S${ZLiftRate}");
		 configuration.getSlicingProfile().setzLiftSpeedCalculator("var value = 0.25;\n"
		 		+ "if ($CURSLICE > $NumFirstLayers) {\n"
		 		+ " value = 4.6666666666666705e+000 * Math.pow($buildAreaMM,0) + -7.0000000000000184e-003 * Math.pow($buildAreaMM,1) + 3.3333333333333490e-006 * Math.pow($buildAreaMM,2);\n"
		 		+ "}\n"
		 		+ "value");
		 configuration.getSlicingProfile().setzLiftDistanceCalculator("var value = 9.0;\n"
		 		+ "if ($CURSLICE > $NumFirstLayers) {\n"
			 	+ " value = 3.5555555555555420e+000 * Math.pow($buildAreaMM,0) + 4.3333333333334060e-003 * Math.pow($buildAreaMM,1) + 1.1111111111110492e-006 * Math.pow($buildAreaMM,2);\n"
			 	+ "}\n"
			 	+ "value");
		 configuration.getSlicingProfile().setExposureTimeCalculator("var value = $FirstLayerTime;\n"
		 		+ "if ($CURSLICE > $NumFirstLayers) {\n"
		 		+ "	value = $LayerTime\n"
		 		+ "}\n"
			 	+ "value");
		 configuration.getSlicingProfile().setProjectorGradientCalculator(			    
		 		"function getFractions(count, start, end) {\n" + 
				"	var incrementAmount = (end - start) / count;\n" +
				"	var fractions = [];\n" + 
				"	for (t = 0; t < count; t++) {\n" +
				"		fractions[t] = start + incrementAmount * t;\n" +
				"	}\n" +
				"	//return new float[]{0, 1};\n" +
				"	return fractions;\n" + 
	 			"}\n" +
	 			"function getColors(fractions, start, stop) {\n" + 
	 			"	var colors = [];\n" +
	 			"	var colorRange = stop - start;\n" + 
	 			"	var atanDivergencePoint = Math.PI / 2;\n" +
	 			"	for (t = 0; t < fractions.length; t++) {\n" +
	 			"		colors[t] = new Packages.java.awt.Color(0, 0, 0, (java.lang.Integer)(Math.atan(fractions[t] * atanDivergencePoint) * colorRange + start));\n" +
				"	}\n" + 
				"	//return new Packages.java.awt.Color[]{new Packages.java.awt.Color(0, 0, 0, (java.lang.Integer)(opacityLevelModel.getValue()/(float)opacityLevelModel.getMaximum())), new Packages.java.awt.Color(0, 0, 0, 0)};\n" +
				"	return colors;\n" + 
	 			"}\n" +
	 			"var bulbCenter = new Packages.java.awt.geom.Point2D.Double($buildPlatformXPixels / 2, $buildPlatformYPixels / 2);\n" +
	 			"var bulbFocus = new Packages.java.awt.geom.Point2D.Double($buildPlatformXPixels / 2, $buildPlatformYPixels / 2);\n" +
	 			"var totalSizeOfGradient = $buildPlatformXPixels > $buildPlatformYPixels?$buildPlatformXPixels:$buildPlatformYPixels;\n" +
	 			"var fractions = getFractions(totalSizeOfGradient, 0, 1);\n" +
	 			"var colors = getColors(fractions, 0.2, 0);//Let's start with 20% opaque in the center of the projector bulb\n" +
	 			"new Packages.java.awt.RadialGradientPaint(\n" +
	 			"	bulbCenter,\n" + 
	 			"	totalSizeOfGradient,\n" +
				"	bulbFocus,\n" +
				"	fractions,\n" + 
				"	colors,\n" +
				"	MultipleGradientPaint.CycleMethod.NO_CYCLE)");
		 
		try {
			return new Printer(configuration);
		} catch (InappropriateDeviceException e) {
			//TODO: Throw an error if this fails!!!
			e.printStackTrace();
			return null;
		}
	 }
	 
	 PrinterConfiguration createTemplatePrinter(String printername, String displayId, String comport, double physicalProjectionMMX, double physicalProjectionMMY, double buildHeightMMZ) {
			PrinterConfiguration currentConfiguration = new PrinterConfiguration(printername, printername);
			ComPortSettings settings = new ComPortSettings();
			settings.setPortName(comport);
			settings.setDatabits(8);
			settings.setHandshake("None");
			settings.setStopbits("One");
			settings.setParity("None");
			settings.setSpeed(115200);
			MotorsDriverConfig motors = new MotorsDriverConfig();
			motors.setComPortSettings(settings);
			MonitorDriverConfig monitor = new MonitorDriverConfig();
			
			MachineConfig machineConfig = new MachineConfig();
			machineConfig.setMotorsDriverConfig(motors);
			machineConfig.setMonitorDriverConfig(monitor);
			machineConfig.setOSMonitorID(displayId);
			machineConfig.setName(printername);
			machineConfig.setPlatformXSize(physicalProjectionMMX);
			machineConfig.setPlatformYSize(physicalProjectionMMY);
			machineConfig.setPlatformZSize(buildHeightMMZ);
			
			SlicingProfile slicingProfile = new SlicingProfile();
			slicingProfile.setLiftDistance(5.0);
			slicingProfile.setLiftFeedRate(50);
			slicingProfile.setDirection(BuildDirection.Bottom_Up);
			try {
				GraphicsDevice device = DisplayManager.Instance().getDisplayDevice(DisplayManager.LAST_AVAILABLE_DISPLAY);
				monitor.setDLP_X_Res(device.getDefaultConfiguration().getBounds().getWidth());
				monitor.setDLP_Y_Res(device.getDefaultConfiguration().getBounds().getHeight());
				machineConfig.setxRenderSize((int)monitor.getDLP_X_Res());
				machineConfig.setyRenderSize((int)monitor.getDLP_Y_Res());
				slicingProfile.setxResolution((int)monitor.getDLP_X_Res());
				slicingProfile.setyResolution((int)monitor.getDLP_Y_Res());
			} catch (InappropriateDeviceException e) {
				e.printStackTrace();
				throw new IllegalArgumentException("Couldn't get screen device");
			}
			
			InkConfig ink = new InkConfig();
			ink.setName("Default");
			ink.setNumberOfFirstLayers(3);
			ink.setResinPriceL(65.0);
			ink.setSliceHeight(0.1);
			ink.setFirstLayerExposureTime(5000);
			ink.setExposureTime(1000);
			
			List<InkConfig> configs = new ArrayList<InkConfig>();
			configs.add(ink);
			
			slicingProfile.setInkConfigs(configs);
			slicingProfile.setSelectedInkConfigName("Default");
			slicingProfile.setDotsPermmX(monitor.getDLP_X_Res() / physicalProjectionMMX);
			slicingProfile.setDotsPermmY(monitor.getDLP_Y_Res() / physicalProjectionMMY);
			slicingProfile.setFlipX(false);
			slicingProfile.setFlipY(true);
			
			currentConfiguration.setSlicingProfile(slicingProfile);
			currentConfiguration.setMachineConfig(machineConfig);
			
			currentConfiguration.setName(printername);
			return currentConfiguration;
	 }

	 
	 
	 
	 
	 
	 
	 
	 //This creates a template printer and saves it.
	 public static void main(String[] args) {
		 PrinterConfiguration configuration = new PrinterService().createTemplatePrinter().getConfiguration();
		 try {
				HostProperties.Instance().addPrinterConfiguration(configuration);
			} catch (AlreadyAssignedException e) {
				e.printStackTrace();
			}

	 }
}