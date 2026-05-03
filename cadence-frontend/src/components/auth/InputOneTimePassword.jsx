import React from "react";
import { Input } from "@material-tailwind/react";

export function InputOneTimePassword() {
  const inputRefs = React.useRef([]);
  const [otp, setOtp] = React.useState(Array(6).fill(""));

  const handleChange = (index, value) => {
    const digit = value.replace(/[^0-9]/g, "");
    if (!digit) return;

    const newOtp = [...otp];
    newOtp[index] = digit;
    setOtp(newOtp);

    if (index < inputRefs.current.length - 1) {
      inputRefs.current[index + 1].focus();
    }
  };

  const handleBackspace = (event, index) => {
    if (event.key === "Backspace") {
      if (otp[index]) {
        // Clear current value
        const newOtp = [...otp];
        newOtp[index] = "";
        setOtp(newOtp);
      } else if (index > 0) {
        // Move to previous and clear that
        inputRefs.current[index - 1]?.focus();
        const newOtp = [...otp];
        newOtp[index - 1] = "";
        setOtp(newOtp);
      }
    }
  };

  return (
    <div className="w-full max-w-sm">
      <div className="my-4 flex items-center justify-center gap-5">
        {otp.map((digit, index) => (
          <React.Fragment key={`otp-${index}`}>
            <Input
              type="text"
              maxLength={1}
              className="!w-12 !h-14 text-white !font-bold text-center !text-xl appearance-none 
                         !border-t-blue-gray-200 focus:!border-white 
                         placeholder:text-blue-gray-300 placeholder:opacity-100"
              labelProps={{
                className: "before:content-none after:content-none",
              }}
              containerProps={{
                className: "!min-w-0 !w-10 !shrink-0",
              }}
              value={digit}
              onChange={(e) => handleChange(index, e.target.value)}
              onKeyDown={(e) => handleBackspace(e, index)}
              inputRef={(el) => (inputRefs.current[index] = el)}
            />
          </React.Fragment>
        ))}
      </div>
    </div>
  );
}
