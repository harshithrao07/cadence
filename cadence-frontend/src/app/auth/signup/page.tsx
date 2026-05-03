"use client";

import React, { useEffect, useState } from "react";
import Image from "next/image";
import Input from "@/components/auth/Input";
import { Button, Checkbox } from "@material-tailwind/react";
import { CustomStepper } from "@/components/auth/CustomStepper";
import Link from "next/link";
import { AuthenticationResponseDTO, RegisterRequestDTO } from "@/types/Auth";
import api from "@/lib/api";
import { toast } from "sonner";
import { useRouter } from "next/navigation";

const Signup = () => {
  const [signupBody, setSignupBody] = useState<RegisterRequestDTO>({
    name: "",
    email: "",
    password: "",
  });
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [activeStep, setActiveStep] = useState<number>(0);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [invalidPasswordConditions, setInvalidPasswordConditions] = useState([
    false,
    false,
    false,
  ]);
  const router = useRouter();

  const passwordConditions = [
    "1 letter",
    "1 number or special character (example: # ? ! &)",
    "10 characters",
  ];
  const hasSpecialOrNumber = /[0-9!@#$%^&*(),.?":{}|<>]/;

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    console.log("Submitting signup form:", signupBody);
    try {
      const response = await api.post(`/auth/v1/register`, {
        ...signupBody,
      }, {
        withCredentials: true,
      });

      const authResponse: AuthenticationResponseDTO = response.data.data;
      localStorage.setItem("auth_details", JSON.stringify(authResponse));

      console.log("Registration success:", response.data);
      toast.success("Registration successful!.");
      router.push("/");
    } catch (err: any) {
      console.error("Registration failed:", err);

      if (err.response) {
        const { data } = err.response;

        if (data?.data && typeof data.data === "object") {
          Object.values(data.data).forEach((msg: any) => {
            toast.error(Array.isArray(msg) ? msg.join(", ") : msg);
          });
        } else {
          toast.error(data?.message || "Something went wrong");
        }
      } else {
        toast.error("Unable to connect to server");
      }
    }
  };

  const validateStep = async (step: number): Promise<boolean> => {
    const newErrors: Record<string, string> = {};

    if (step === 0) {
      if (!signupBody.email.trim()) {
        newErrors.email = "Email is required";
      } else if (!signupBody.email.includes("@")) {
        newErrors.email = "Invalid email";
      } else {
        try {
          const response = await api.post(
            `/auth/v1/validateEmail`,
            signupBody.email
          );

          if (response.data.data) {
            newErrors.email = response.data.message;
          }
        } catch (err) {
          console.error("Email validation failed:", err);
          newErrors.email = "Unable to validate email";
        }
      }
    }

    if (step === 1) {
      const hasSpecialOrNumber = /[0-9!@#$%^&*]/;

      if (!signupBody.password.trim()) {
        newErrors.password = "Password is required";
      } else if (!hasSpecialOrNumber.test(signupBody.password)) {
        newErrors.password =
          "Password must contain a number or special character";
      } else if (signupBody.password.length < 10) {
        newErrors.password = "Password must contain at least 10 characters";
      }
    }

    if (step === 2) {
      if (!signupBody.name.trim()) {
        newErrors.name = "Name is required";
      }
    }

    setErrors(newErrors);

    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (event) => {
    const { name, value } = event.target;
    setSignupBody((prevValue) => ({
      ...prevValue,
      [name]: value,
    }));

    if (name === "email") {
      if (value.trim().length > 0) {
        setErrors({});
      }

      if (value.includes("@")) {
        setErrors({});
      }
    } else if (name === "password") {
      const updatedConditions = [
        value.trim().length > 0, // not empty
        hasSpecialOrNumber.test(value), // contains special char or number
        value.length >= 10, // at least 10 characters
      ];

      if (
        updatedConditions[0] &&
        updatedConditions[1] &&
        updatedConditions[2]
      ) {
        setErrors({});
      }
      setInvalidPasswordConditions(updatedConditions);
    } else if (name === "name") {
      if (value.trim().length > 0) {
        setErrors({});
      }
    }
  };

  const handleToggle = () => {
    setShowPassword(!showPassword);
  };

  const handleNext = async () => {
    const isValid = await validateStep(activeStep);
    if (isValid) {
      if (activeStep < 2) {
        setActiveStep((prev) => prev + 1);
      } else {
        handleSubmit();
      }
    }
  };

  const handlePrevious = () => {
    setActiveStep((prev) => prev - 1);
  };

  useEffect(() => {
    setShowPassword(false);
  }, [activeStep]);

  return (
    <div className="flex flex-col items-center h-screen justify-center">
      <div className="flex flex-col items-center justify-center gap-y-5">
        <Image
          src="/images/cadence-logo.png"
          width={50}
          height={50}
          alt="Cadence"
          style={{ width: "auto", height: "auto" }}
        />
        {activeStep == 0 && (
          <h1 className="text-5xl font-semibold text-center">
            Sign up to
            <br /> start listening
          </h1>
        )}
      </div>

      {activeStep != 0 && (
        <CustomStepper activeStep={activeStep} setActiveStep={setActiveStep} />
      )}

      <div className="flex flex-col items-center w-full h-1/2">
        <div className="flex flex-col items-center justify-center gap-y-4 w-1/3">
          <div className="self-start flex items-center gap-x-6 mb-3">
            {activeStep != 0 && (
              <svg
                onClick={handlePrevious}
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                strokeWidth={1.5}
                stroke="currentColor"
                className="size-8 cursor-pointer"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M15.75 19.5 8.25 12l7.5-7.5"
                />
              </svg>
            )}

            <div>
              {activeStep != 0 && (
                <p className="text-gray-500">Step {activeStep} of 3</p>
              )}
              {activeStep == 1 && <p>Create a password</p>}
              {activeStep == 2 && <p>Tell us about yourself</p>}
            </div>
          </div>

          <form onSubmit={handleSubmit} className="flex flex-col w-full">
            {activeStep == 0 && (
              <>
                <Input
                  type="email"
                  placeholder="Email Address"
                  name="email"
                  value={signupBody.email}
                  onChange={handleChange}
                  required={true}
                  errorText={errors["email"]}
                  htmlFor={""}
                  handleToggle={handleToggle}
                  showPassword={false}
                />
              </>
            )}

            {activeStep === 1 && (
              <div>
                <Input
                  type={showPassword ? "text" : "password"}
                  placeholder="Password"
                  name="password"
                  value={signupBody.password}
                  onChange={handleChange}
                  handleToggle={handleToggle}
                  showPassword={showPassword}
                  required={true}
                  errorText={errors["password"]}
                  htmlFor={""}
                />
                <div className="mt-8 text-sm">
                  Your password must contain at least
                  <br />
                  {passwordConditions.map((condition, index) => (
                    <Checkbox
                      key={`password-condition-${index}`}
                      {...({
                        disabled: true,
                        checked: invalidPasswordConditions[index],
                        className: "rounded-full h-3 w-3",
                        label: condition,
                        labelProps: { className: "text-gray-100" },
                        icon: (
                          <svg
                            xmlns="http://www.w3.org/2000/svg"
                            viewBox="0 0 24 24"
                            fill="currentColor"
                            className="size-5 text-red-500"
                          >
                            <path
                              fillRule="evenodd"
                              d="M2.25 12c0-5.385 4.365-9.75 9.75-9.75s9.75 4.365 9.75 9.75-4.365 9.75-9.75 9.75S2.25 17.385 2.25 12Zm13.36-1.814a.75.75 0 1 0-1.22-.872l-3.236 4.53L9.53 12.22a.75.75 0 0 0-1.06 1.06l2.25 2.25a.75.75 0 0 0 1.14-.094l3.75-5.25Z"
                              clipRule="evenodd"
                            />
                          </svg>
                        ),
                        ripple: false,
                      } as any)}
                    />
                  ))}
                </div>
              </div>
            )}

            {activeStep === 2 && (
              <div className="flex flex-col gap-y-6">
                <div className="flex flex-col">
                  <label className="text-sm text-gray-500 mb-2">
                    This name will appear on your profile
                  </label>
                  <Input
                    type="text"
                    name="name"
                    value={signupBody.name}
                    onChange={handleChange}
                    required={true}
                    placeholder={"Name"}
                    errorText={errors["name"]}
                    htmlFor={""}
                    handleToggle={handleToggle}
                    showPassword={false}
                  />
                </div>
              </div>
            )}

            <div className="mt-6">
              <Button
                {...({
                  ripple: false,
                  color: "red",
                  className:
                    "w-full text-black rounded-full shadow-none hover:shadow-none hover:bg-red-400 normal-case text-md",
                  variant: "filled",
                  onClick: handleNext,
                } as any)}
              >
                {activeStep !== 2 ? "Next" : "Sign up"}
              </Button>
            </div>
          </form>

          {activeStep == 0 && (
            <>
              <div className="flex items-center w-full">
                <div className="flex-grow h-px bg-gray-600"></div>
                <span className="mx-2 text-white text-md font-medium">or</span>
                <div className="flex-grow h-px bg-gray-600"></div>
              </div>

              <Button
                placeholder=""
                onResize={undefined}
                onResizeCapture={undefined}
                onPointerEnterCapture={undefined}
                onPointerLeaveCapture={undefined}
                variant="outlined"
                color="blue-gray"
                onClick={() => window.location.href = `${process.env.NEXT_PUBLIC_API_URL}oauth2/authorization/google`}
                className="relative w-full rounded-full normal-case text-md font-outfit text-white hover:border-white hover:opacity-100"
              >
                <Image
                  src="https://docs.material-tailwind.com/icons/google.svg"
                  alt="google"
                  height={20}
                  width={20}
                  className="absolute left-4 top-1/2 -translate-y-1/2"
                />

                <span className="block w-full text-center">
                  Sign up with Google
                </span>
              </Button>

              <hr className="mt-4 border-t w-full border-gray-800" />

              <p className="text-gray-500">
                Already have an account?{" "}
                <Link className="underline text-white" href="/auth/login">
                  Log in here
                </Link>
                .
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default Signup;
