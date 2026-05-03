import React from "react";
import { Input as MTInput } from "@material-tailwind/react";
import { FaEye, FaEyeSlash } from "react-icons/fa";
import { InputWithHelperText } from "./InputHelperText";

const Input = ({
  type,
  placeholder,
  name,
  value,
  onChange,
  htmlFor,
  handleToggle,
  showPassword,
  required,
  errorText,
}) => {
  const hasError = Boolean(errorText && errorText.trim());

  return (
    <div>
      <label htmlFor={htmlFor} className="capitalize">
        {htmlFor}
      </label>
      <div className="flex flex-col">
        <MTInput
          type={type}
          color="white"
          label={placeholder}
          icon={
            name === "password" &&
            (showPassword ? (
              <FaEye
                onClick={handleToggle}
                size={18}
                className="fas fa-heart cursor-pointer"
              />
            ) : (
              <FaEyeSlash
                size={18}
                onClick={handleToggle}
                className="cursor-pointer"
              />
            ))
          }
          value={value}
          name={name}
          onChange={onChange}
          required={required}
          error={hasError}
        />
        {hasError && <InputWithHelperText text={errorText} />}
      </div>
    </div>
  );
};

export default Input;
