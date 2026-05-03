import React from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

export default function BirthdayPicker({ setDateOfBirth }) {
  const [date, setDate] = React.useState(null);
  const [currentMonth, setCurrentMonth] = React.useState(new Date().getMonth());
  const [currentYear, setCurrentYear] = React.useState(
    new Date().getFullYear()
  );
  const [open, setOpen] = React.useState(false);
  const popoverRef = React.useRef(null);

  const today = new Date();
  const minYear = 1900;
  const maxYear = today.getFullYear();

  React.useEffect(() => {
    const handleClickOutside = (event) => {
      if (popoverRef.current && !popoverRef.current.contains(event.target)) {
        setOpen(false);
      }
    };

    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
    }

    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const formatDate = (date) => {
    if (!date) return "";
    const months = [
      "January",
      "February",
      "March",
      "April",
      "May",
      "June",
      "July",
      "August",
      "September",
      "October",
      "November",
      "December",
    ];
    return `${
      months[date.getMonth()]
    } ${date.getDate()}, ${date.getFullYear()}`;
  };

  const getDaysInMonth = (month, year) => {
    return new Date(year, month + 1, 0).getDate();
  };

  const getFirstDayOfMonth = (month, year) => {
    return new Date(year, month, 1).getDay();
  };

  const handlePrevMonth = () => {
    if (currentMonth === 0) {
      if (currentYear > minYear) {
        setCurrentMonth(11);
        setCurrentYear(currentYear - 1);
      }
    } else {
      setCurrentMonth(currentMonth - 1);
    }
  };

  const handleNextMonth = () => {
    const isCurrentMonth =
      currentYear === maxYear && currentMonth === today.getMonth();
    if (!isCurrentMonth) {
      if (currentMonth === 11) {
        if (currentYear < maxYear) {
          setCurrentMonth(0);
          setCurrentYear(currentYear + 1);
        }
      } else {
        setCurrentMonth(currentMonth + 1);
      }
    }
  };

  const handleDateSelect = (day) => {
    const selectedDate = new Date(currentYear, currentMonth, day);
    if (selectedDate <= today) {
      setDate(selectedDate);
      setDateOfBirth(selectedDate);
      setOpen(false);
    }
  };

  const isDateDisabled = (day) => {
    const checkDate = new Date(currentYear, currentMonth, day);
    return checkDate > today;
  };

  const isSelectedDate = (day) => {
    if (!date) return false;
    return (
      date.getDate() === day &&
      date.getMonth() === currentMonth &&
      date.getFullYear() === currentYear
    );
  };

  const isToday = (day) => {
    return (
      today.getDate() === day &&
      today.getMonth() === currentMonth &&
      today.getFullYear() === currentYear
    );
  };

  const renderCalendar = () => {
    const daysInMonth = getDaysInMonth(currentMonth, currentYear);
    const firstDay = getFirstDayOfMonth(currentMonth, currentYear);
    const days = [];
    const monthNames = [
      "January",
      "February",
      "March",
      "April",
      "May",
      "June",
      "July",
      "August",
      "September",
      "October",
      "November",
      "December",
    ];

    for (let i = 0; i < firstDay; i++) {
      days.push(<div key={`empty-${i}`} className="h-9 w-9" />);
    }

    for (let day = 1; day <= daysInMonth; day++) {
      const disabled = isDateDisabled(day);
      const selected = isSelectedDate(day);
      const todayDate = isToday(day);

      days.push(
        <button
          key={`day-${day}`}
          onClick={() => !disabled && handleDateSelect(day)}
          disabled={disabled}
          className={`h-9 w-9 rounded-md text-sm font-normal transition-colors
            ${selected ? "bg-blue-600 text-white hover:bg-blue-700" : ""}
            ${
              todayDate && !selected
                ? "bg-gray-700 text-white font-semibold"
                : ""
            }
            ${!selected && !todayDate ? "hover:bg-gray-700 text-gray-200" : ""}
            ${
              disabled
                ? "text-gray-600 opacity-30 cursor-not-allowed hover:bg-transparent"
                : "cursor-pointer"
            }
          `}
        >
          {day}
        </button>
      );
    }

    const canGoPrev = !(currentYear === minYear && currentMonth === 0);
    const canGoNext = !(
      currentYear === maxYear && currentMonth === today.getMonth()
    );

    return (
      <div className="p-4 bg-gray-800 rounded-lg border border-gray-700 text-white shadow-xl">
        <div className="flex justify-center items-center gap-3 mb-4">
          <button
            onClick={handlePrevMonth}
            disabled={!canGoPrev}
            className="h-6 w-6 bg-gray-700 hover:bg-gray-600 p-1 rounded-md transition-colors disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-gray-700"
          >
            <ChevronLeft className="h-4 w-4 text-white" />
          </button>

          <select
            value={currentMonth}
            onChange={(e) => setCurrentMonth(Number(e.target.value))}
            className="bg-gray-700 text-white rounded px-2 py-1 text-sm border border-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {monthNames.map((month, idx) => (
              <option key={`month-${idx}`} value={idx}>
                {month}
              </option>
            ))}
          </select>

          <select
            value={currentYear}
            onChange={(e) => setCurrentYear(Number(e.target.value))}
            className="bg-gray-700 text-white rounded px-2 py-1 text-sm border border-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {Array.from(
              { length: maxYear - minYear + 1 },
              (_, i) => maxYear - i
            ).map((year) => (
              <option key={year} value={year}>
                {year}
              </option>
            ))}
          </select>

          <button
            onClick={handleNextMonth}
            disabled={!canGoNext}
            className="h-6 w-6 bg-gray-700 hover:bg-gray-600 p-1 rounded-md transition-colors disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-gray-700"
          >
            <ChevronRight className="h-4 w-4 text-white" />
          </button>
        </div>

        <div className="grid grid-cols-7 gap-1 mb-2">
          {["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"].map((day) => (
            <div
              key={`weekday-${day}`}
              className="h-9 w-9 flex items-center justify-center text-sm font-medium text-gray-400"
            >
              {day}
            </div>
          ))}
        </div>

        <div className="grid grid-cols-7 gap-1">{days}</div>
      </div>
    );
  };

  return (
    <div className="w-full max-w-sm" ref={popoverRef}>
      <div className="relative">
        <input
          type="text"
          readOnly
          onClick={() => setOpen(!open)}
          value={formatDate(date)}
          placeholder="Select your birthday"
          className="w-full px-3 py-2 bg-gray-900 text-white border border-gray-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 cursor-pointer"
        />

        {open && (
          <div className="absolute top-full left-0 mt-2 z-50">
            {renderCalendar()}
          </div>
        )}
      </div>
    </div>
  );
}
