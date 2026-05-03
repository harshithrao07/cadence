// CustomStepper.jsx
import React from "react";
import { Stepper, Step } from "@material-tailwind/react";

export function CustomStepper({ activeStep, setActiveStep }) {
  return (
    <div className="w-full max-w-md px-6 mt-10 mb-5">
      <Stepper
        activeStep={activeStep}
        lineClassName="bg-white/50"
        activeLineClassName="bg-white"
      >
        {[0, 1, 2].map((step) => (
          <Step
            key={`step-${step}`}
            className="h-0 w-0 bg-transparent"
            activeClassName="ring-0 !bg-white text-white"
            completedClassName="!bg-white text-white"
          >
          </Step>
        ))}
      </Stepper>
    </div>
  );
}
