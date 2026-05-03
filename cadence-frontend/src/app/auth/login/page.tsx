"use client";

import React, { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { Button } from "@material-tailwind/react";
import Input from "@/components/auth/Input";
import { InputOneTimePassword } from "@/components/auth/InputOneTimePassword";
import api from "@/lib/api";
import { AuthenticationResponseDTO } from "@/types/Auth";
import { toast } from "sonner";
import { useRouter } from "next/navigation";

const Login = () => {
  const [loginBody, setLoginBody] = useState({
    email: "",
    password: "",
  });
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const router = useRouter();

  const handleChange = (event) => {
    const { name, value } = event.target;
    setLoginBody((prevValue) => ({
      ...prevValue,
      [name]: value,
    }));
  };

  const handleToggle = () => {
    setShowPassword(!showPassword);
  };

  const handleLogin = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const newErrors: Record<string, string> = {};

    if (loginBody.email.trim() === "") {
      newErrors.email = "Email is required";
    } else if (loginBody.password.trim() === "") {
      newErrors.password = "Password is required";
    }

    setErrors(newErrors);
    if (newErrors.email || newErrors.password) return;

    try {
      const response = await api.post(`/auth/v1/authenticate`, loginBody, {
        withCredentials: true,
      });

      const authResponse: AuthenticationResponseDTO = response.data.data;
      localStorage.setItem("auth_details", JSON.stringify(authResponse));

      console.log("Login success:", response.data);
      toast.success("Logged in successful!.");
      router.push("/");
    } catch (err: any) {
      console.error("Login failed:", err);

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

  return (
    <div className="container mx-auto flex justify-center items-center min-h-screen m-8">
      <div className="w-full max-w-2xl bg-gray-900 p-8 rounded-xl shadow-lg flex flex-col items-center gap-y-6">
        <div className="flex flex-col items-center justify-center gap-y-2">
          <Image
            src="/images/cadence-logo.png"
            width={40}
            height={40}
            alt="Cadence"
            style={{ width: "auto", height: "auto" }}
          />
          <h1 className="text-4xl font-semibold text-center">
            Log in to Cadence
          </h1>
        </div>

        <div className="flex flex-col items-center justify-center gap-y-8 w-full">
          <Button
            placeholder=""
            onResize={undefined}
            onResizeCapture={undefined}
            onPointerEnterCapture={undefined}
            onPointerLeaveCapture={undefined}
            variant="outlined"
            ripple={false}
            color="blue-gray"
            onClick={() => window.location.href = `${process.env.NEXT_PUBLIC_API_URL}oauth2/authorization/google`}
            className="relative w-1/2 rounded-full normal-case text-md font-outfit text-white hover:border-white hover:opacity-100"
          >
            <Image
              src="https://docs.material-tailwind.com/icons/google.svg"
              alt="google"
              height={20}
              width={20}
              className="absolute left-4 top-1/2 -translate-y-1/2"
            />

            <span className="block w-full text-center">
              Continue with Google
            </span>
          </Button>

          <hr className="border-t w-5/6 border-gray-800" />

          <form className="flex flex-col w-1/2 gap-y-3">
            <div>
              <Input
                type="email"
                name="email"
                value={loginBody.email}
                onChange={handleChange}
                errorText={errors["email"]}
                placeholder="Email Address"
                htmlFor={""}
                handleToggle={handleToggle}
                showPassword={false}
                required={true}
              />
            </div>

            <div>
              <Input
                type={showPassword ? "text" : "password"}
                placeholder="Password"
                name="password"
                value={loginBody.password}
                onChange={handleChange}
                handleToggle={handleToggle}
                showPassword={showPassword}
                errorText={errors["password"]}
                htmlFor={""}
                required={true}
              />
            </div>

            <Button
              {...({
                ripple: false,
                color: "red",
                className:
                  "w-full mt-3 text-black rounded-full shadow-none hover:shadow-none hover:bg-red-400 normal-case text-md",
                variant: "filled",
                onClick: handleLogin,
              } as any)}
            >
              Log in
            </Button>
          </form>
          <p className="text-gray-500">
            Don&apos;t have an account?{" "}
            <Link className="underline text-white" href="/auth/signup">
              Sign up for Cadence
            </Link>
            .
          </p>
        </div>
      </div>
    </div>
  );
};

export default Login;
